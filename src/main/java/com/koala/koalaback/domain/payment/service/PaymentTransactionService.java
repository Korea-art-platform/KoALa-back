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

    public record CancelContext(Long paymentId, String providerCode,
                                String pgTransactionId, BigDecimal cancelAmount,
                                boolean partial) {
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
