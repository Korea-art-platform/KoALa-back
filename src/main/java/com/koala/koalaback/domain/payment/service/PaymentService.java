package com.koala.koalaback.domain.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.entity.Payment;
import com.koala.koalaback.domain.payment.entity.PaymentEvent;
import com.koala.koalaback.domain.payment.provider.PaymentProvider;
import com.koala.koalaback.domain.payment.repository.PaymentEventRepository;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.infra.slack.AdminAlertNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final String NICEPAY = "NICEPAY";

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final CodeGenerator codeGenerator;
    private final List<PaymentProvider> providers;
    private final ObjectMapper objectMapper;
    private final PaymentTransactionService paymentTransactionService;

    private final AdminAlertNotifier adminAlertNotifier;

    @Transactional
    public PaymentDto.PrepareResponse prepare(Long userId, PaymentDto.PrepareRequest req) {
        Order order = orderRepository.findByOrderNo(req.getOrderNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!"PENDING_PAYMENT".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
        }

        Payment payment = Payment.builder()
                .order(order)
                .paymentNo(codeGenerator.generatePaymentNo())
                .provider(req.getProvider())
                .method(req.getMethod())
                .requestedAmount(order.getTotalAmount())
                .build();
        paymentRepository.save(payment);

        recordEvent(payment, "READY", "SUCCESS", order.getTotalAmount(), null, null);

        return PaymentDto.PrepareResponse.builder()
                .paymentNo(payment.getPaymentNo())
                .orderNo(order.getOrderNo())
                .amount(order.getTotalAmount())
                .provider(req.getProvider())
                .method(req.getMethod())
                .build();
    }

    public PaymentDto.PaymentResponse confirm(Long userId, PaymentDto.ConfirmRequest req) {
        return runConfirm(paymentTransactionService.beginConfirm(userId, req), req);
    }

    /**
     * PG 서명으로 이미 인증된 승인 — 나이스 결제창 복귀 경로 전용.
     *
     * <p>호출자가 서명을 검증했다는 전제다. 검증 없이 부르면 아무나 결제를 승인시킬 수 있다.
     * 승인 이후의 흐름(보상 취소·미확정 처리)은 로그인 경로와 완전히 같다.
     */
    public PaymentDto.PaymentResponse confirmVerifiedByPg(PaymentDto.ConfirmRequest req) {
        return runConfirm(paymentTransactionService.beginConfirmVerifiedByPg(req), req);
    }

    private PaymentDto.PaymentResponse runConfirm(PaymentTransactionService.ConfirmContext ctx,
                                                  PaymentDto.ConfirmRequest req) {
        PaymentProvider provider = getProvider(ctx.providerCode());

        PaymentProvider.PaymentConfirmResult result;
        try {
            result = provider.confirm(req.getPaymentKey(), ctx.orderNo(), ctx.amount());
        } catch (Exception e) {
            log.error("PG 승인 호출 중 예외 — 승인 여부 미확정: orderNo={}", ctx.orderNo(), e);
            result = PaymentProvider.PaymentConfirmResult.unknown("PROVIDER_EXCEPTION", e.getMessage());
        }

        if (result.isApproved()) {
            return applyApprovedOrCompensate(provider, ctx, result);
        }

        if (result.isUnknown()) {
            return resolveUnknownConfirm(provider, ctx, result);
        }

        paymentTransactionService.applyConfirmRejected(
                ctx.paymentId(), result.failureCode(), result.failureMessage());
        throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, result.failureMessage());
    }

    private PaymentDto.PaymentResponse applyApprovedOrCompensate(
            PaymentProvider provider,
            PaymentTransactionService.ConfirmContext ctx,
            PaymentProvider.PaymentConfirmResult result) {
        try {
            return paymentTransactionService.applyConfirmApproved(ctx.paymentId(), result);
        } catch (Exception e) {
            log.error("승인 반영 실패 — 보상 취소를 시작한다: orderNo={}, pgTransactionId={}",
                    ctx.orderNo(), result.pgTransactionId(), e);
            compensateApproved(provider, ctx, result);
            throw new BusinessException(ErrorCode.PAYMENT_IN_DOUBT);
        }
    }

    private void compensateApproved(PaymentProvider provider,
                                    PaymentTransactionService.ConfirmContext ctx,
                                    PaymentProvider.PaymentConfirmResult result) {
        String pgTransactionId = result.pgTransactionId();
        BigDecimal amount = result.approvedAmount() != null ? result.approvedAmount() : ctx.amount();

        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            log.error("★수동 확인 필요★ 거래번호가 없어 보상 취소 불가 — orderNo={}, amount={}",
                    ctx.orderNo(), amount);
            adminAlertNotifier.notifyManualRefundNeeded(
                    ctx.orderNo(), amount, null, "거래번호가 없어 취소를 호출할 수 없습니다.");
            recordSafely(() -> paymentTransactionService.applyConfirmInDoubt(
                    ctx.paymentId(), "COMPENSATE_NO_TX_ID", "저장 실패 후 거래번호 없어 취소 불가"));
            return;
        }

        PaymentProvider.PaymentCancelResult cancelResult;
        try {
            cancelResult = provider.cancel(pgTransactionId, amount, "결제 저장 실패에 따른 보상 취소");
        } catch (Exception e) {
            log.error("보상 취소 호출 중 예외: orderNo={}, pgTransactionId={}",
                    ctx.orderNo(), pgTransactionId, e);
            cancelResult = PaymentProvider.PaymentCancelResult.unknown(
                    "COMPENSATE_EXCEPTION", e.getMessage());
        }

        if (cancelResult.isCancelled()) {
            log.warn("보상 취소 성공 — 승인이 철회됐다: orderNo={}, pgTransactionId={}, amount={}",
                    ctx.orderNo(), pgTransactionId, amount);

            recordSafely(() -> paymentTransactionService.applyConfirmRejected(
                    ctx.paymentId(), "SAVE_FAILED_COMPENSATED", "저장 실패로 승인을 취소했습니다."));
            return;
        }

        log.error("★수동 환불 필요★ 보상 취소 실패 — 고객 결제금이 남아 있을 수 있다. "
                        + "orderNo={}, pgTransactionId={}, amount={}, code={}, message={}",
                ctx.orderNo(), pgTransactionId, amount,
                cancelResult.failureCode(), cancelResult.failureMessage());

        adminAlertNotifier.notifyManualRefundNeeded(
                ctx.orderNo(), amount, pgTransactionId, cancelResult.failureMessage());

        recordSafely(() -> paymentTransactionService.applyConfirmInDoubt(
                ctx.paymentId(), "COMPENSATE_FAILED", "저장 실패 후 보상 취소도 실패했습니다."));
    }

    private void recordSafely(Runnable dbWrite) {
        try {
            dbWrite.run();
        } catch (Exception e) {
            log.error("보상 결과 DB 기록 실패 — 로그로만 남는다", e);
        }
    }

    private PaymentDto.PaymentResponse resolveUnknownConfirm(
            PaymentProvider provider,
            PaymentTransactionService.ConfirmContext ctx,
            PaymentProvider.PaymentConfirmResult result) {
        log.warn("PG 응답 미확정 — 재조회 시도: orderNo={}, code={}", ctx.orderNo(), result.failureCode());

        PaymentProvider.PaymentLookupResult lookup;
        try {
            lookup = provider.lookup(ctx.orderNo());
        } catch (Exception e) {
            log.error("재조회 중 예외: orderNo={}", ctx.orderNo(), e);
            lookup = PaymentProvider.PaymentLookupResult.unavailable();
        }

        if (lookup.approved()) {
            log.info("재조회 결과 승인됨 — 정상 확정: orderNo={}", ctx.orderNo());

            return applyApprovedOrCompensate(provider, ctx,
                    PaymentProvider.PaymentConfirmResult.approved(
                            lookup.pgTransactionId(), lookup.approvalNo(),
                            lookup.approvedAmount() != null ? lookup.approvedAmount() : ctx.amount(),
                            lookup.rawResponse()));
        }

        if (lookup.isDefinitelyNotApproved()) {
            log.info("재조회 결과 미승인 확정 — 실패 처리: orderNo={}", ctx.orderNo());
            paymentTransactionService.applyConfirmRejected(
                    ctx.paymentId(), result.failureCode(), result.failureMessage());
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, result.failureMessage());
        }

        paymentTransactionService.applyConfirmInDoubt(
                ctx.paymentId(), result.failureCode(), result.failureMessage());
        adminAlertNotifier.notifyPaymentInDoubt(
                ctx.orderNo(), ctx.amount(), "PG 재조회 실패 — 승인 여부를 알 수 없습니다.");
        throw new BusinessException(ErrorCode.PAYMENT_IN_DOUBT);
    }

    public PaymentDto.PaymentResponse cancel(String paymentNo, PaymentDto.CancelRequest req) {
        PaymentTransactionService.CancelContext ctx =
                paymentTransactionService.beginCancel(paymentNo, req);

        PaymentProvider provider = getProvider(ctx.providerCode());

        PaymentProvider.PaymentCancelResult result;
        try {
            result = provider.cancel(ctx.pgTransactionId(), ctx.cancelAmount(), req.getReason());
        } catch (Exception e) {
            log.error("PG 취소 호출 중 예외 — 취소 여부 미확정: paymentNo={}", paymentNo, e);
            result = PaymentProvider.PaymentCancelResult.unknown("PROVIDER_EXCEPTION", e.getMessage());
        }

        if (result.isCancelled()) {
            return paymentTransactionService.applyCancelSucceeded(
                    ctx.paymentId(), ctx.cancelAmount(), result.rawResponse());
        }

        if (result.isUnknown()) {
            paymentTransactionService.applyCancelInDoubt(
                    ctx.paymentId(), ctx.cancelAmount(), result.failureMessage());
            adminAlertNotifier.notifyCancelInDoubt(
                    paymentNo, ctx.cancelAmount(), result.failureMessage());
            throw new BusinessException(ErrorCode.PAYMENT_IN_DOUBT);
        }

        paymentTransactionService.applyCancelRejected(
                ctx.paymentId(), ctx.cancelAmount(), result.failureCode(), result.failureMessage());
        throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, result.failureMessage());
    }

    @Transactional
    public void handleWebhook(String providerCode, String payloadJson) {
        log.info("Webhook received: provider={}", providerCode);
        String paymentKey = extractTransactionId(providerCode, payloadJson);
        if (paymentKey.isBlank()) {
            log.warn("Webhook paymentKey 추출 실패 — payload: {}", payloadJson);
            return;
        }
        BigDecimal webhookAmount = extractWebhookAmount(providerCode, payloadJson);
        paymentRepository.findByPgTransactionId(paymentKey)
                .ifPresentOrElse(
                        payment -> {
                            String status = extractWebhookStatus(providerCode, payloadJson);
                            recordEvent(payment, "WEBHOOK", status, BigDecimal.ZERO, paymentKey, payloadJson);
                            settleInDoubtByWebhook(payment, status, webhookAmount, paymentKey, payloadJson);
                            log.info("Webhook processed: paymentKey={}, status={}", paymentKey, status);
                        },
                        () -> log.warn("Webhook — 매핑된 결제 없음: paymentKey={}", paymentKey)
                );
    }

    private void settleInDoubtByWebhook(Payment payment, String status, BigDecimal webhookAmount,
                                        String paymentKey, String payloadJson) {
        if (!payment.isInDoubt()) {
            return;
        }
        if ("DONE".equals(status)) {
            // 서명이 맞아도 금액까지 맞는지는 따로 본다. 서명은 "PG 가 보냈다"를 보증할 뿐,
            // 그 PG 가 우리가 청구한 금액을 승인했는지는 우리 장부와 대조해야 알 수 있다.
            // 어긋나면 자동으로 확정하지 않고 사람에게 넘긴다 — 돈이 걸린 판단이다.
            if (webhookAmount != null
                    && webhookAmount.compareTo(payment.getRequestedAmount()) != 0) {
                log.error("★웹훅 금액 불일치★ 자동 확정하지 않는다: paymentNo={}, 청구={}, 웹훅={}",
                        payment.getPaymentNo(), payment.getRequestedAmount(), webhookAmount);
                recordEvent(payment, "WEBHOOK_AMOUNT_MISMATCH", "FAILED",
                        webhookAmount, paymentKey, payloadJson);
                adminAlertNotifier.notifyPaymentInDoubt(
                        payment.getOrder().getOrderNo(), payment.getRequestedAmount(),
                        "웹훅 금액 불일치 (웹훅 " + webhookAmount + "원) — 직접 확인 필요");
                return;
            }
            payment.markCaptured(paymentKey, null, payment.getRequestedAmount(), payloadJson);
            payment.getOrder().markPaid();
            recordEvent(payment, "CAPTURED", "SUCCESS",
                    payment.getRequestedAmount(), paymentKey, payloadJson);
            log.info("웹훅으로 미확정 결제 승인 확정: paymentNo={}, orderNo={}",
                    payment.getPaymentNo(), payment.getOrder().getOrderNo());
        } else if ("ABORTED".equals(status) || "EXPIRED".equals(status) || "CANCELED".equals(status)) {
            payment.markFailed("WEBHOOK_" + status, "웹훅으로 미승인 확정");
            payment.getOrder().markPaymentFailed();
            log.info("웹훅으로 미확정 결제 실패 확정: paymentNo={}, status={}",
                    payment.getPaymentNo(), status);
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentDto.PaymentResponse> getPaymentsNeedingAttention() {
        return paymentRepository
                .findByStatusInOrderByCreatedAtDesc(
                        List.of("IN_DOUBT", "IN_PROGRESS", "CANCEL_IN_PROGRESS"))
                .stream()
                .map(PaymentDto.PaymentResponse::from)
                .toList();
    }

    @Transactional
    public void recordRefundFailure(String paymentNo, String reason) {
        paymentRepository.findByPaymentNo(paymentNo).ifPresent(payment ->
                recordEvent(payment, "REFUND_FAILED", "FAILED", BigDecimal.ZERO, null, reason)
        );
    }

    private PaymentProvider getProvider(String providerCode) {
        return providers.stream()
                .filter(p -> p.getProviderCode().equals(providerCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR,
                        "지원하지 않는 PG사: " + providerCode));
    }

    private void recordEvent(Payment payment, String eventType, String eventStatus,
                             BigDecimal amount, String providerEventId, String payloadJson) {
        paymentEventRepository.save(PaymentEvent.builder()
                .payment(payment)
                .eventType(eventType)
                .eventStatus(eventStatus)
                .amount(amount)
                .providerEventId(providerEventId)
                .payloadJson(payloadJson)
                .build());
    }

    private String extractTransactionId(String providerCode, String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(payloadJson);

            // 나이스는 전문이 평면이고 거래키 이름도 tid 다 — data.paymentKey 를 찾으면 못 찾는다
            if (NICEPAY.equals(providerCode)) {
                return root.path("tid").asText("");
            }

            JsonNode dataNode = root.path("data");
            if (!dataNode.isMissingNode()) {
                String key = dataNode.path("paymentKey").asText("");
                if (!key.isBlank()) return key;
            }

            return root.path("paymentKey").asText("");
        } catch (Exception e) {
            log.warn("Webhook payload 파싱 실패: {}", e.getMessage());
            return "";
        }
    }

    private String extractWebhookStatus(String providerCode, String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            if (NICEPAY.equals(providerCode)) {
                return toInternalStatus(root.path("status").asText(""));
            }
            String status = root.path("data").path("status").asText("");
            return status.isBlank() ? "UNKNOWN" : status;
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * 나이스 상태값을 내부 어휘로 옮긴다.
     *
     * <p>{@code partialCancelled} 와 {@code ready} 는 일부러 어디에도 걸리지 않는 값으로 보낸다.
     * 미확정 결제를 정리하는 규칙이 CANCELED 를 "실패 확정"으로 다루기 때문이다.
     * 부분취소는 <b>승인이 있었다는 뜻</b>이고, ready 는 가상계좌를 발급했을 뿐 아직 입금 전이다.
     * 둘 다 실패로 적으면 멀쩡한 주문이 실패로 뒤집힌다.
     */
    private String toInternalStatus(String niceStatus) {
        return switch (niceStatus) {
            case "paid" -> "DONE";
            case "failed" -> "ABORTED";
            case "cancelled" -> "CANCELED";
            case "expired" -> "EXPIRED";
            case "partialCancelled" -> "PARTIAL_CANCELED";
            case "ready" -> "READY";
            default -> "UNKNOWN";
        };
    }

    /**
     * 전문에 적힌 결제 금액. 없으면 null 을 돌려 금액 대조를 건너뛴다.
     */
    private BigDecimal extractWebhookAmount(String providerCode, String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            JsonNode amount = NICEPAY.equals(providerCode)
                    ? root.path("amount")
                    : root.path("data").path("totalAmount");
            return amount.isNumber() || amount.isTextual()
                    ? new BigDecimal(amount.asText())
                    : null;
        } catch (Exception e) {
            return null;
        }
    }
}
