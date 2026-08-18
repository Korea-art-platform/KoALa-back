package com.koala.koalaback.domain.payment.service;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.event.OrderCompletedEvent;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.entity.Payment;
import com.koala.koalaback.domain.payment.entity.PaymentEvent;
import com.koala.koalaback.domain.payment.provider.PaymentProvider;
import com.koala.koalaback.domain.payment.repository.PaymentEventRepository;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;

    private final ApplicationEventPublisher eventPublisher;

    public record ConfirmContext(Long paymentId, String providerCode,
                                 String orderNo, BigDecimal amount) {}

    /**
     * @param cancelAmount 우리 장부에 적을 환불 금액. 전액이든 부분이든 항상 값이 있다
     * @param partial      승인액보다 적게 돌려주는 경우
     */
    public record CancelContext(Long paymentId, String providerCode,
                                String pgTransactionId, BigDecimal cancelAmount,
                                boolean partial) {

        /**
         * PG 에 넘길 금액. <b>전액 취소면 null 이다.</b>
         *
         * <p>PG 들은 금액이 없으면 전액 취소로, 있으면 부분 취소로 읽는다. 전액인데도 금액을 실어
         * 보내면 부분취소 요청이 되고, 계좌이체·휴대폰처럼 부분취소를 지원하지 않는 수단에서는
         * 환불이 통째로 거절된다.
         */
        public BigDecimal amountForProvider() {
            return partial ? cancelAmount : null;
        }
    }

    @Transactional
    public ConfirmContext beginConfirm(Long userId, PaymentDto.ConfirmRequest req) {
        Order order = orderRepository.findByOrderNo(req.getOrderNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return beginConfirmInternal(order, req);
    }

    /**
     * 로그인 세션 없이 승인을 시작한다 — <b>PG 서명으로 이미 인증된 요청 전용</b>.
     *
     * <p>나이스 결제창은 인증이 끝나면 우리 서버로 크로스사이트 POST 를 보낸다.
     * 그 요청에는 세션 쿠키가 실리지 않아 {@code userId} 를 알 수 없다.
     * 대신 서명({@code sha256(authToken + clientId + amount + secretKey)})이 인증 역할을 한다 —
     * secretKey 를 모르면 만들 수 없고, 금액까지 해시에 들어가 위변조가 막힌다.
     *
     * <p><b>호출 전에 반드시 서명을 검증해야 한다.</b> 검증 없이 부르면 아무나 결제를
     * 승인시킬 수 있다. 그래서 이름에 그 전제를 박아 두었다.
     *
     * <p>소유권 확인만 빠지고 나머지 안전장치(중복 승인 차단·금액 대조·IN_PROGRESS 선점)는
     * 그대로 탄다.
     */
    @Transactional
    public ConfirmContext beginConfirmVerifiedByPg(PaymentDto.ConfirmRequest req) {
        Order order = orderRepository.findByOrderNo(req.getOrderNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        return beginConfirmInternal(order, req);
    }

    private ConfirmContext beginConfirmInternal(Order order, PaymentDto.ConfirmRequest req) {
        Payment payment = paymentRepository
                .findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.isInProgress()) {
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
        if (payment.isInDoubt()) {
            throw new BusinessException(ErrorCode.PAYMENT_IN_DOUBT);
        }
        if (!payment.isReady()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        if (payment.getRequestedAmount().compareTo(req.getAmount()) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        payment.markInProgress();
        recordEvent(payment, "CONFIRM_REQUESTED", "PENDING", req.getAmount(), null, null);

        return new ConfirmContext(payment.getId(), payment.getProvider(),
                order.getOrderNo(), payment.getRequestedAmount());
    }

    @Transactional
    public PaymentDto.PaymentResponse applyConfirmApproved(
            Long paymentId, PaymentProvider.PaymentConfirmResult result) {
        Payment payment = getPayment(paymentId);

        if (payment.isCaptured()) {
            log.info("이미 승인 확정된 결제 — 재적용 생략: paymentNo={}", payment.getPaymentNo());
            return PaymentDto.PaymentResponse.from(payment);
        }

        payment.markCaptured(result.pgTransactionId(), result.approvalNo(),
                result.approvedAmount(), result.rawResponse());

        Order order = payment.getOrder();
        order.markPaid();

        recordEvent(payment, "CAPTURED", "SUCCESS",
                result.approvedAmount(), result.pgTransactionId(), result.rawResponse());

        eventPublisher.publishEvent(toCompletedEvent(order));

        log.info("결제 승인 확정: paymentNo={}, orderNo={}", payment.getPaymentNo(), order.getOrderNo());
        return PaymentDto.PaymentResponse.from(payment);
    }

    @Transactional
    public void applyConfirmRejected(Long paymentId, String failureCode, String failureMessage) {
        Payment payment = getPayment(paymentId);
        payment.markFailed(failureCode, failureMessage);
        payment.getOrder().markPaymentFailed();
        recordEvent(payment, "FAILED", "FAILED",
                payment.getRequestedAmount(), null, failureMessage);
        log.info("결제 거절 확정: paymentNo={}, code={}", payment.getPaymentNo(), failureCode);
    }

    @Transactional
    public void applyConfirmInDoubt(Long paymentId, String failureCode, String failureMessage) {
        Payment payment = getPayment(paymentId);
        payment.markInDoubt(failureCode, failureMessage);
        recordEvent(payment, "CONFIRM_IN_DOUBT", "UNKNOWN",
                payment.getRequestedAmount(), null, failureMessage);
        log.error("결제 승인 여부 미확정 — 수동/웹훅 확인 필요: paymentNo={}, orderNo={}, code={}",
                payment.getPaymentNo(), payment.getOrder().getOrderNo(), failureCode);
    }

    @Transactional
    public CancelContext beginCancel(String paymentNo, PaymentDto.CancelRequest req) {
        Payment payment = paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.isCaptured()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        BigDecimal cancelAmount = req.getCancelAmount() != null
                ? req.getCancelAmount() : payment.getApprovedAmount();

        if (cancelAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "환불 금액은 1원 이상이어야 합니다.");
        }
        if (cancelAmount.compareTo(payment.getApprovedAmount()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "환불 금액이 승인 금액(" + payment.getApprovedAmount().toPlainString() + "원)을 넘을 수 없습니다.");
        }

        payment.markCancelInProgress();
        recordEvent(payment, "CANCEL_REQUESTED", "PENDING", cancelAmount, null, null);

        boolean partial = cancelAmount.compareTo(payment.getApprovedAmount()) < 0;

        return new CancelContext(payment.getId(), payment.getProvider(),
                payment.getPgTransactionId(), cancelAmount, partial);
    }

    @Transactional
    public PaymentDto.PaymentResponse applyCancelSucceeded(
            Long paymentId, BigDecimal cancelAmount, String rawResponse) {
        Payment payment = getPayment(paymentId);
        payment.markCancelled(cancelAmount);
        recordEvent(payment, "CANCELLED", "SUCCESS", cancelAmount, null, rawResponse);
        log.info("환불 확정: paymentNo={}, amount={}", payment.getPaymentNo(), cancelAmount);
        return PaymentDto.PaymentResponse.from(payment);
    }

    @Transactional
    public void applyCancelRejected(Long paymentId, BigDecimal cancelAmount,
                                    String failureCode, String failureMessage) {
        Payment payment = getPayment(paymentId);
        payment.revertToCaptured();
        recordEvent(payment, "CANCELLED", "FAILED", cancelAmount, null, failureMessage);
        log.error("환불 거절: paymentNo={}, code={}, message={}",
                payment.getPaymentNo(), failureCode, failureMessage);
    }

    @Transactional
    public void applyCancelInDoubt(Long paymentId, BigDecimal cancelAmount, String failureMessage) {
        Payment payment = getPayment(paymentId);
        payment.markInDoubt("CANCEL_UNKNOWN", failureMessage);
        recordEvent(payment, "CANCEL_IN_DOUBT", "UNKNOWN", cancelAmount, null, failureMessage);
        log.error("환불 여부 미확정 — 수동 확인 필요: paymentNo={}, amount={}",
                payment.getPaymentNo(), cancelAmount);
    }

    private Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void recordEvent(Payment payment, String eventType, String eventStatus,
                             BigDecimal amount, String providerEventId, String payloadJson) {
        paymentEventRepository.save(PaymentEvent.builder()
                .payment(payment)
                .eventType(eventType)
                .eventStatus(eventStatus)
                .amount(amount)
                .providerEventId(providerEventId)
                // 실패 사유는 사람이 읽는 문장으로 들어온다. payload_json 은 JSON 칼럼이라
                // 그대로 넣으면 저장이 거부되고, 기록하려던 트랜잭션까지 뒤집힌다
                .payloadJson(PaymentEventPayload.normalize(payloadJson))
                .build());
    }

    private OrderCompletedEvent toCompletedEvent(Order order) {
        List<OrderCompletedEvent.Item> items = order.getOrderItems().stream()
                .map(i -> new OrderCompletedEvent.Item(
                        i.getSkuCodeSnapshot(),
                        i.getSkuNameSnapshot(),
                        i.getArtistNameSnapshot(),
                        i.getQuantity(),
                        i.getLineTotalAmount()))
                .toList();

        return OrderCompletedEvent.of(
                order.getId(),
                order.getOrderNo(),
                order.getUser() != null ? order.getUser().getId() : null,
                order.getOrdererName(),
                order.getOrdererEmail(),
                order.getProductAmount(),
                order.getShippingAmount(),
                order.getTotalAmount(),
                items);
    }
}
