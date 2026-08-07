package com.koala.koalaback.domain.payment.service;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.entity.Payment;
import com.koala.koalaback.domain.payment.provider.PaymentProvider;
import com.koala.koalaback.domain.payment.repository.PaymentEventRepository;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 결제 승인 DB 단계 검증 — 사전 검증과 상태 전이.
 *
 * <p>PG 호출은 이 클래스에 들어오지 않으므로(설계상 트랜잭션 밖) 여기서는
 * "검증이 제대로 막는가", "성공 시 주문이 PAID 로 가는가"만 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("결제 승인 DB 단계")
class PaymentTransactionServiceTest {

    @InjectMocks private PaymentTransactionService paymentTransactionService;

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentEventRepository paymentEventRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("금액이 다르면 승인을 거부한다 — 위변조된 금액으로 결제되지 않는다")
    void beginConfirm_amountMismatch_isRejected() {
        // given — 주문은 53,000원인데 승인 요청은 1,000원
        Order order = givenOrder();
        Payment payment = givenPayment(order, "READY", BigDecimal.valueOf(53_000));
        given(orderRepository.findByOrderNo("ORD-1")).willReturn(Optional.of(order));
        given(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(any()))
                .willReturn(Optional.of(payment));

        PaymentDto.ConfirmRequest req = confirmRequest(BigDecimal.valueOf(1_000));

        // when & then
        assertThatThrownBy(() -> paymentTransactionService.beginConfirm(1L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

        assertThat(payment.getStatus()).as("선점되지 않아야 한다").isEqualTo("READY");
    }

    @Test
    @DisplayName("사전 검증 통과 시 IN_PROGRESS 로 선점한다 — 같은 결제의 이중 승인 차단")
    void beginConfirm_valid_marksInProgress() {
        Order order = givenOrder();
        Payment payment = givenPayment(order, "READY", BigDecimal.valueOf(53_000));
        given(orderRepository.findByOrderNo("ORD-1")).willReturn(Optional.of(order));
        given(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(any()))
                .willReturn(Optional.of(payment));

        PaymentTransactionService.ConfirmContext ctx = paymentTransactionService
                .beginConfirm(1L, confirmRequest(BigDecimal.valueOf(53_000)));

        assertThat(payment.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(ctx.orderNo()).isEqualTo("ORD-1");
        assertThat(ctx.amount()).isEqualByComparingTo(BigDecimal.valueOf(53_000));
    }

    @Test
    @DisplayName("이미 IN_PROGRESS 인 결제는 다시 승인 요청할 수 없다")
    void beginConfirm_alreadyInProgress_isBlocked() {
        Order order = givenOrder();
        Payment payment = givenPayment(order, "IN_PROGRESS", BigDecimal.valueOf(53_000));
        given(orderRepository.findByOrderNo("ORD-1")).willReturn(Optional.of(order));
        given(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(any()))
                .willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentTransactionService
                .beginConfirm(1L, confirmRequest(BigDecimal.valueOf(53_000))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS));
    }

    @Test
    @DisplayName("승인 성공 시 결제는 CAPTURED, 주문은 PAID 로 함께 전이된다")
    void applyConfirmApproved_transitionsOrderToPaid() {
        Order order = givenOrder();
        Payment payment = givenPayment(order, "IN_PROGRESS", BigDecimal.valueOf(53_000));
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));

        paymentTransactionService.applyConfirmApproved(10L,
                PaymentProvider.PaymentConfirmResult.approved(
                        "pk_1", "A1", BigDecimal.valueOf(53_000), "{}"));

        assertThat(payment.getStatus()).isEqualTo("CAPTURED");
        assertThat(order.getOrderStatus()).as("주문이 결제완료로 전이").isEqualTo("PAID");
        // 후처리(메일)는 커밋 후 처리되도록 이벤트로만 발행한다
        then(eventPublisher).should().publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("PG 거절 시 주문은 PAID 가 되지 않는다")
    void applyConfirmRejected_doesNotMarkOrderPaid() {
        Order order = givenOrder();
        Payment payment = givenPayment(order, "IN_PROGRESS", BigDecimal.valueOf(53_000));
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));

        paymentTransactionService.applyConfirmRejected(10L, "REJECT", "한도 초과");

        assertThat(payment.getStatus()).isEqualTo("FAILED");
        assertThat(order.getOrderStatus()).as("주문 상태는 결제완료가 아니어야 한다").isNotEqualTo("PAID");
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("미확정 시 결제만 IN_DOUBT 가 되고 주문 상태는 건드리지 않는다")
    void applyConfirmInDoubt_leavesOrderUntouched() {
        Order order = givenOrder();
        String before = order.getOrderStatus();
        Payment payment = givenPayment(order, "IN_PROGRESS", BigDecimal.valueOf(53_000));
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));

        paymentTransactionService.applyConfirmInDoubt(10L, "TOSS_NO_RESPONSE", "read timed out");

        assertThat(payment.getStatus()).isEqualTo("IN_DOUBT");
        assertThat(payment.isSettlementPending())
                .as("만료 스케줄러가 건너뛰어야 할 상태").isTrue();
        assertThat(order.getOrderStatus()).as("주문은 그대로").isEqualTo(before);
    }

    // ── Helpers ───────────────────────────────────────────

    private Order givenOrder() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);

        Order order = Order.builder()
                .orderNo("ORD-1")
                .user(user)
                .productAmount(BigDecimal.valueOf(50_000))
                .discountAmount(BigDecimal.ZERO)
                .shippingAmount(BigDecimal.valueOf(3_000))
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(53_000))
                .ordererName("주문자")
                .ordererEmail("orderer@koala.test")
                .ordererPhone("01011112222")
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "orderItems", List.of());
        return order;
    }

    private Payment givenPayment(Order order, String status, BigDecimal requestedAmount) {
        Payment payment = Payment.builder()
                .order(order)
                .paymentNo("PAY-1")
                .provider("TOSS")
                .method("CARD")
                .requestedAmount(requestedAmount)
                .build();
        ReflectionTestUtils.setField(payment, "id", 10L);
        ReflectionTestUtils.setField(payment, "status", status);
        return payment;
    }

    private PaymentDto.ConfirmRequest confirmRequest(BigDecimal amount) {
        PaymentDto.ConfirmRequest req = mock(PaymentDto.ConfirmRequest.class);
        given(req.getOrderNo()).willReturn("ORD-1");
        given(req.getPaymentKey()).willReturn("pk_1");
        given(req.getAmount()).willReturn(amount);
        return req;
    }
}
