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

/**
 * 결제 흐름의 <b>DB 단계</b>만 담당한다. 외부 PG 호출은 이 클래스에 들어오지 않는다.
 *
 * <p>{@link PaymentService} 가 이 빈을 주입받아 호출하는 이유는 자기호출(self-invocation) 때문이다.
 * 같은 클래스 안에서 {@code this.begin...()} 를 부르면 Spring 프록시를 타지 않아
 * {@code @Transactional} 이 무시되고, 결국 "트랜잭션 밖에서 PG 호출"이라는 목적이 깨진다.
 * 별도 빈으로 두면 반드시 프록시를 경유하므로 각 단계가 실제로 독립 트랜잭션이 된다.
 *
 * <p>각 메서드는 짧게 끝나야 한다 — 이 트랜잭션이 열려 있는 동안 DB 커넥션이 점유된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    /** 주문 후처리 이벤트 발행 — 실제 전송은 커밋 후 OrderEventRelay 가 한다 */
    private final ApplicationEventPublisher eventPublisher;

    /** PG 호출에 필요한 최소 정보 — 엔티티를 트랜잭션 밖으로 내보내지 않기 위한 스냅샷 */
    public record ConfirmContext(Long paymentId, String providerCode,
                                 String orderNo, BigDecimal amount) {}

    public record CancelContext(Long paymentId, String providerCode,
                                String pgTransactionId, BigDecimal cancelAmount) {}

    // ── 승인 ──────────────────────────────────────────────

    /**
     * ① 사전 검증 + 선점. 커밋된 뒤에야 PG 를 호출한다.
     *
     * <p>결제를 IN_PROGRESS 로 바꿔 커밋하므로, 같은 결제에 대한 두 번째 승인 요청은
     * 여기서 걸러진다(이중 결제 방지).
     */
    @Transactional
    public ConfirmContext beginConfirm(Long userId, PaymentDto.ConfirmRequest req) {
        Order order = orderRepository.findByOrderNo(req.getOrderNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Payment payment = paymentRepository
                .findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.isInProgress()) {
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
        if (payment.isInDoubt()) {
            // 승인됐을 수 있는 건을 다시 승인 요청하면 이중 결제가 된다.
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

    /**
     * ③-a 승인 확정 — 결제와 주문 상태를 함께 바꾼다.
     *
     * <p>이 두 갱신이 한 트랜잭션에 있으므로 "결제는 CAPTURED 인데 주문은 미결제" 가 생기지 않는다.
     */
    @Transactional
    public PaymentDto.PaymentResponse applyConfirmApproved(
            Long paymentId, PaymentProvider.PaymentConfirmResult result) {

        Payment payment = getPayment(paymentId);

        // 웹훅이 먼저 확정했을 수 있다 — 이미 CAPTURED 면 그대로 둔다(멱등)
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

        // 주문 후처리(메일 등)는 이벤트로 분리한다.
        // 여기서는 발행만 하고, 실제 전송은 커밋된 뒤 OrderEventRelay 가 담당한다
        // (@TransactionalEventListener AFTER_COMMIT). 롤백되면 이벤트도 나가지 않는다.
        eventPublisher.publishEvent(toCompletedEvent(order));

        log.info("결제 승인 확정: paymentNo={}, orderNo={}", payment.getPaymentNo(), order.getOrderNo());
        return PaymentDto.PaymentResponse.from(payment);
    }

    /** ③-b 승인 거절 확정 — PG 가 명시적으로 거절한 경우에만 호출한다 */
    @Transactional
    public void applyConfirmRejected(Long paymentId, String failureCode, String failureMessage) {
        Payment payment = getPayment(paymentId);
        payment.markFailed(failureCode, failureMessage);
        payment.getOrder().markPaymentFailed();
        recordEvent(payment, "FAILED", "FAILED",
                payment.getRequestedAmount(), null, failureMessage);
        log.info("결제 거절 확정: paymentNo={}, code={}", payment.getPaymentNo(), failureCode);
    }

    /**
     * ③-c 승인 여부 미확정 — 주문 상태는 건드리지 않는다.
     *
     * <p>주문을 PAYMENT_FAILED 로 내리면 승인된 결제가 실패로 보이고,
     * PAID 로 올리면 승인 안 된 주문이 결제 완료로 보인다. 둘 다 위험하므로
     * 주문은 그대로 두고 결제만 IN_DOUBT 로 표시해 만료 취소 대상에서 제외한다.
     */
    @Transactional
    public void applyConfirmInDoubt(Long paymentId, String failureCode, String failureMessage) {
        Payment payment = getPayment(paymentId);
        payment.markInDoubt(failureCode, failureMessage);
        recordEvent(payment, "CONFIRM_IN_DOUBT", "UNKNOWN",
                payment.getRequestedAmount(), null, failureMessage);
        log.error("결제 승인 여부 미확정 — 수동/웹훅 확인 필요: paymentNo={}, orderNo={}, code={}",
                payment.getPaymentNo(), payment.getOrder().getOrderNo(), failureCode);
    }

    // ── 취소/환불 ──────────────────────────────────────────

    /** ① 환불 사전 검증 + 선점 */
    @Transactional
    public CancelContext beginCancel(String paymentNo, PaymentDto.CancelRequest req) {
        Payment payment = paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.isCaptured()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        BigDecimal cancelAmount = req.getCancelAmount() != null
                ? req.getCancelAmount() : payment.getApprovedAmount();

        // 승인 금액보다 많이 돌려줄 수는 없다. 반품 쪽에서 한 번 걸러지지만
        // 환불 경로가 그것만은 아니므로 돈이 나가는 지점에서 한 번 더 막는다.
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

        return new CancelContext(payment.getId(), payment.getProvider(),
                payment.getPgTransactionId(), cancelAmount);
    }

    /** ③-a 환불 확정 */
    @Transactional
    public PaymentDto.PaymentResponse applyCancelSucceeded(
            Long paymentId, BigDecimal cancelAmount, String rawResponse) {

        Payment payment = getPayment(paymentId);
        payment.markCancelled(cancelAmount);
        recordEvent(payment, "CANCELLED", "SUCCESS", cancelAmount, null, rawResponse);
        log.info("환불 확정: paymentNo={}, amount={}", payment.getPaymentNo(), cancelAmount);
        return PaymentDto.PaymentResponse.from(payment);
    }

    /** ③-b 환불 거절 — 선점을 풀고 원래 상태로 되돌린다 */
    @Transactional
    public void applyCancelRejected(Long paymentId, BigDecimal cancelAmount,
                                    String failureCode, String failureMessage) {
        Payment payment = getPayment(paymentId);
        payment.revertToCaptured();
        recordEvent(payment, "CANCELLED", "FAILED", cancelAmount, null, failureMessage);
        log.error("환불 거절: paymentNo={}, code={}, message={}",
                payment.getPaymentNo(), failureCode, failureMessage);
    }

    /**
     * ③-c 환불 여부 미확정 — CAPTURED 로 되돌리지 않는다.
     * 되돌리면 다시 환불을 시도해 이중 환불이 날 수 있다.
     */
    @Transactional
    public void applyCancelInDoubt(Long paymentId, BigDecimal cancelAmount, String failureMessage) {
        Payment payment = getPayment(paymentId);
        payment.markInDoubt("CANCEL_UNKNOWN", failureMessage);
        recordEvent(payment, "CANCEL_IN_DOUBT", "UNKNOWN", cancelAmount, null, failureMessage);
        log.error("환불 여부 미확정 — 수동 확인 필요: paymentNo={}, amount={}",
                payment.getPaymentNo(), cancelAmount);
    }

    // ── Private helpers ───────────────────────────────────

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
                .payloadJson(payloadJson)
                .build());
    }

    /**
     * 이벤트 페이로드 생성 — 지연로딩 컬렉션(orderItems)에 접근하므로
     * 반드시 트랜잭션이 열려 있는 이 시점에 스냅샷을 떠야 한다.
     * 컨슈머는 커밋 이후 다른 스레드에서 돌기 때문에 엔티티를 넘길 수 없다.
     */
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
