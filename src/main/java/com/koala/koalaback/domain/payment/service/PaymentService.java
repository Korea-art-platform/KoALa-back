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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 결제 오케스트레이션.
 *
 * <p><b>클래스 레벨에 {@code @Transactional} 을 두지 않는다.</b>
 * 승인·취소는 "짧은 트랜잭션 → 외부 PG 호출(트랜잭션 밖) → 짧은 트랜잭션" 순서로 진행되며,
 * 여기에 트랜잭션이 걸리면 PG 응답을 기다리는 내내 DB 커넥션이 묶인다.
 * DB 단계는 모두 {@link PaymentTransactionService} 에 있다(자기호출 회피용 별도 빈).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final CodeGenerator codeGenerator;
    private final List<PaymentProvider> providers;
    private final ObjectMapper objectMapper;
    private final PaymentTransactionService paymentTransactionService;

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

    /**
     * 결제 승인.
     *
     * <pre>
     * ① beginConfirm       [트랜잭션] 검증 + IN_PROGRESS 선점 → 커밋
     * ② provider.confirm   [트랜잭션 밖] PG HTTP 호출 (connect 3s / read 10s)
     * ③ apply*             [트랜잭션] 결과 반영 → 커밋
     * </pre>
     *
     * <p>②에서 응답을 못 받으면(UNKNOWN) 승인됐는지 알 수 없으므로 즉시 재조회로 확정을 시도하고,
     * 그래도 모르면 IN_DOUBT 로 남긴다. 절대 FAILED 로 단정하지 않는다.
     */
    public PaymentDto.PaymentResponse confirm(Long userId, PaymentDto.ConfirmRequest req) {
        // ① 사전 검증 + 선점 (짧은 트랜잭션)
        PaymentTransactionService.ConfirmContext ctx =
                paymentTransactionService.beginConfirm(userId, req);

        PaymentProvider provider = getProvider(ctx.providerCode());

        // ② PG 승인 — 트랜잭션 밖
        PaymentProvider.PaymentConfirmResult result;
        try {
            result = provider.confirm(req.getPaymentKey(), ctx.orderNo(), ctx.amount());
        } catch (Exception e) {
            // provider 가 예외를 삼키도록 되어 있지만, 새 구현이 던질 수 있으므로 방어한다.
            // 여기서 실패로 단정하면 안 된다 — 요청이 전달돼 승인됐을 수 있다.
            log.error("PG 승인 호출 중 예외 — 승인 여부 미확정: orderNo={}", ctx.orderNo(), e);
            result = PaymentProvider.PaymentConfirmResult.unknown("PROVIDER_EXCEPTION", e.getMessage());
        }

        // ③ 결과 반영 (짧은 트랜잭션)
        if (result.isApproved()) {
            return paymentTransactionService.applyConfirmApproved(ctx.paymentId(), result);
        }

        if (result.isUnknown()) {
            return resolveUnknownConfirm(provider, ctx, result);
        }

        paymentTransactionService.applyConfirmRejected(
                ctx.paymentId(), result.failureCode(), result.failureMessage());
        throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, result.failureMessage());
    }

    /**
     * 승인 여부 미확정 해소 — 가장 위험한 케이스.
     *
     * <p>"승인은 됐는데 응답을 못 받은" 상황에서 실패로 처리하면 돈은 빠져나갔는데
     * 주문은 만료 취소된다. 그래서 순서가 중요하다.
     * <ol>
     *   <li>PG 에 재조회한다 → 승인됐으면 정상 확정(주문 PAID)</li>
     *   <li>재조회가 성공했고 승인 안 된 게 확인되면 그때만 실패 확정</li>
     *   <li>재조회조차 실패하면 IN_DOUBT — 주문 상태는 손대지 않고 웹훅/수동 확인에 맡긴다</li>
     * </ol>
     */
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
            return paymentTransactionService.applyConfirmApproved(
                    ctx.paymentId(),
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
        throw new BusinessException(ErrorCode.PAYMENT_IN_DOUBT);
    }

    /**
     * 결제 취소(환불) — 승인과 동일하게 3단계로 분리한다.
     *
     * <pre>
     * ① beginCancel      [트랜잭션] 검증 + CANCEL_IN_PROGRESS 선점 → 커밋
     * ② provider.cancel  [트랜잭션 밖] PG HTTP 호출
     * ③ apply*           [트랜잭션] 결과 반영 → 커밋
     * </pre>
     *
     * <p>호출자(주문취소·반품승인)의 트랜잭션 안에서 부르면 다시 PG 호출이 트랜잭션에 갇히므로,
     * 반드시 호출자의 트랜잭션 <b>밖</b>에서 호출해야 한다.
     */
    public PaymentDto.PaymentResponse cancel(String paymentNo, PaymentDto.CancelRequest req) {
        // ① 검증 + 선점
        PaymentTransactionService.CancelContext ctx =
                paymentTransactionService.beginCancel(paymentNo, req);

        PaymentProvider provider = getProvider(ctx.providerCode());

        // ② PG 취소 — 트랜잭션 밖
        PaymentProvider.PaymentCancelResult result;
        try {
            result = provider.cancel(ctx.pgTransactionId(), ctx.cancelAmount(), req.getReason());
        } catch (Exception e) {
            log.error("PG 취소 호출 중 예외 — 취소 여부 미확정: paymentNo={}", paymentNo, e);
            result = PaymentProvider.PaymentCancelResult.unknown("PROVIDER_EXCEPTION", e.getMessage());
        }

        // ③ 결과 반영
        if (result.isCancelled()) {
            return paymentTransactionService.applyCancelSucceeded(
                    ctx.paymentId(), ctx.cancelAmount(), result.rawResponse());
        }

        if (result.isUnknown()) {
            // 되돌리면 재시도로 이중 환불이 날 수 있어 IN_DOUBT 로 잠근다.
            paymentTransactionService.applyCancelInDoubt(
                    ctx.paymentId(), ctx.cancelAmount(), result.failureMessage());
            throw new BusinessException(ErrorCode.PAYMENT_IN_DOUBT);
        }

        paymentTransactionService.applyCancelRejected(
                ctx.paymentId(), ctx.cancelAmount(), result.failureCode(), result.failureMessage());
        throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, result.failureMessage());
    }

    /**
     * PG 웹훅 처리.
     *
     * <p>IN_DOUBT(승인 여부 미확정) 결제를 확정짓는 두 번째 경로이기도 하다.
     * 승인 응답을 못 받아 미확정으로 남은 건도 웹훅이 DONE 을 알려주면 여기서 주문이 PAID 가 된다.
     */
    @Transactional
    public void handleWebhook(String providerCode, String payloadJson) {
        log.info("Webhook received: provider={}", providerCode);
        String paymentKey = extractTransactionId(payloadJson);
        if (paymentKey.isBlank()) {
            log.warn("Webhook paymentKey 추출 실패 — payload: {}", payloadJson);
            return;
        }
        paymentRepository.findByPgTransactionId(paymentKey)
                .ifPresentOrElse(
                        payment -> {
                            String status = extractWebhookStatus(payloadJson);
                            recordEvent(payment, "WEBHOOK", status, BigDecimal.ZERO, paymentKey, payloadJson);
                            settleInDoubtByWebhook(payment, status, paymentKey, payloadJson);
                            log.info("Webhook processed: paymentKey={}, status={}", paymentKey, status);
                        },
                        () -> log.warn("Webhook — 매핑된 결제 없음: paymentKey={}", paymentKey)
                );
    }

    /**
     * 미확정 결제를 웹훅 상태로 확정한다.
     * 이 메서드는 {@link #handleWebhook} 의 트랜잭션 안에서 동작한다(엔티티 변경 감지).
     */
    private void settleInDoubtByWebhook(Payment payment, String status,
                                        String paymentKey, String payloadJson) {
        if (!payment.isInDoubt()) {
            return;
        }
        if ("DONE".equals(status)) {
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

    /**
     * 사람이 확인해야 하는 결제 목록 — 어드민 대시보드용.
     *
     * <p>PG 응답을 못 받아 미확정(IN_DOUBT)으로 잠긴 건은 자동으로 풀리지 않을 수 있다.
     * 웹훅이 오면 확정되지만, 안 오면 아무도 모르게 방치된다. 그래서 노출 경로가 필요하다.
     */
    @Transactional(readOnly = true)
    public List<PaymentDto.PaymentResponse> getPaymentsNeedingAttention() {
        return paymentRepository
                .findByStatusInOrderByCreatedAtDesc(
                        List.of("IN_DOUBT", "IN_PROGRESS", "CANCEL_IN_PROGRESS"))
                .stream()
                .map(PaymentDto.PaymentResponse::from)
                .toList();
    }

    /**
     * 환불 실패 이벤트 기록 — 주문 취소/반품 승인 후 환불이 실패했을 때 감사 추적용
     */
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

    /**
     * Toss Payments 웹훅 payload에서 paymentKey 추출
     * Toss 웹훅 형식: { "eventType": "...", "data": { "paymentKey": "...", ... } }
     */
    private String extractTransactionId(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            // Toss 표준 웹훅 형식: data.paymentKey
            JsonNode dataNode = root.path("data");
            if (!dataNode.isMissingNode()) {
                String key = dataNode.path("paymentKey").asText("");
                if (!key.isBlank()) return key;
            }
            // 최상위 paymentKey fallback (일부 이벤트 타입)
            return root.path("paymentKey").asText("");
        } catch (Exception e) {
            log.warn("Webhook payload 파싱 실패: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 웹훅 이벤트 상태 추출
     * Toss 웹훅: data.status (DONE, CANCELED, PARTIAL_CANCELED, etc.)
     */
    private String extractWebhookStatus(String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            String status = root.path("data").path("status").asText("");
            return status.isBlank() ? "UNKNOWN" : status;
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}