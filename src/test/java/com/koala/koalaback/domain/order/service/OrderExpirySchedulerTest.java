package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.entity.Payment;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.service.StockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("미결제 주문 만료")
class OrderExpirySchedulerTest {
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private StockService stockService;
    @Mock private OrderService orderService;

    @Test
    @DisplayName("30분 초과 미결제 주문의 재고가 복원되고 주문이 취소된다")
    void expirePendingOrder_restoresStockAndCancels() {
        Order order = givenOrder("PENDING_PAYMENT", 3);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(1L)).willReturn(Optional.empty());

        OrderService service = orderServiceWithMocks();

        service.expirePendingOrder(1L);

        then(stockService).should().restore(eq(100L), eq(3), eq("order_expiry"), anyLong());
        assertThat(order.getOrderStatus()).as("주문이 취소됨").isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("이미 결제된 주문은 건드리지 않는다 — 재고가 이중으로 풀리면 안 된다")
    void expirePendingOrder_paidOrder_isUntouched() {
        Order order = givenOrder("PAID", 3);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        OrderService service = orderServiceWithMocks();

        service.expirePendingOrder(1L);

        then(stockService).should(never()).restore(anyLong(), anyInt(), anyString(), anyLong());
        assertThat(order.getOrderStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("승인 여부 미확정 결제가 붙은 주문은 만료 취소하지 않는다")
    void expirePendingOrder_settlementPending_isSkipped() {
        Order order = givenOrder("PENDING_PAYMENT", 3);
        Payment payment = mock(Payment.class);
        given(payment.isSettlementPending()).willReturn(true);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(payment));

        OrderService service = orderServiceWithMocks();

        service.expirePendingOrder(1L);

        then(stockService).should(never()).restore(anyLong(), anyInt(), anyString(), anyLong());
        assertThat(order.getOrderStatus())
                .as("결제 확정 대기 중인 주문은 그대로 둔다").isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("스케줄러는 만료 기준을 넘긴 PENDING 주문만 골라 처리를 위임한다")
    void scheduler_delegatesOnlyExpiredPendingOrders() {
        Order expired = givenOrder("PENDING_PAYMENT", 1);
        given(orderRepository.findByOrderStatusAndCreatedAtBefore(eq("PENDING_PAYMENT"), any(LocalDateTime.class)))
                .willReturn(List.of(expired));

        OrderExpiryScheduler scheduler = new OrderExpiryScheduler(orderRepository, orderService);
        ReflectionTestUtils.setField(scheduler, "timeoutMinutes", 30L);

        scheduler.releaseExpiredPendingOrders();

        then(orderService).should().expirePendingOrder(1L);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 주문 처리는 계속된다")
    void scheduler_continuesAfterFailure() {
        Order first = givenOrder("PENDING_PAYMENT", 1);
        Order second = givenOrder("PENDING_PAYMENT", 1);
        ReflectionTestUtils.setField(second, "id", 2L);

        given(orderRepository.findByOrderStatusAndCreatedAtBefore(anyString(), any(LocalDateTime.class)))
                .willReturn(List.of(first, second));
        org.mockito.BDDMockito.willThrow(new RuntimeException("일시적 오류"))
                .given(orderService).expirePendingOrder(1L);

        OrderExpiryScheduler scheduler = new OrderExpiryScheduler(orderRepository, orderService);
        ReflectionTestUtils.setField(scheduler, "timeoutMinutes", 30L);

        scheduler.releaseExpiredPendingOrders();

        then(orderService).should().expirePendingOrder(2L);
    }

    private OrderService orderServiceWithMocks() {
        return new OrderService(
                orderRepository, null, null, null,
                stockService, null, paymentRepository, null,
                null, null, null);
    }

    private Order givenOrder(String status, int quantity) {
        Order order = Order.builder()
                .orderNo("ORD-1")
                .user(null)
                .productAmount(java.math.BigDecimal.TEN)
                .discountAmount(java.math.BigDecimal.ZERO)
                .shippingAmount(java.math.BigDecimal.ZERO)
                .taxAmount(java.math.BigDecimal.ZERO)
                .totalAmount(java.math.BigDecimal.TEN)
                .ordererName("주문자")
                .ordererEmail("orderer@koala.test")
                .ordererPhone("01011112222")
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "orderStatus", status);

        Sku sku = mock(Sku.class);
        given(sku.getId()).willReturn(100L);
        OrderItem item = mock(OrderItem.class);
        given(item.getSku()).willReturn(sku);
        given(item.getQuantity()).willReturn(quantity);
        given(item.getId()).willReturn(500L);
        ReflectionTestUtils.setField(order, "orderItems", new java.util.ArrayList<>(List.of(item)));

        return order;
    }
}
