package com.koala.koalaback.domain.payment.repository;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.entity.Payment;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("대시보드 매출 집계")
class PaymentRevenueQueryTest extends IntegrationTestSupport {
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PaymentService paymentService;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment_events");
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM orders");
    }

    @Test
    @DisplayName("환불·취소는 빼고, 부분 환불은 남은 금액만 더한다")
    void revenueExcludesRefundsAndCancellations() {
        paid(10_000, 0, false);
        paid(20_000, 20_000, false);
        paid(30_000, 5_000, false);
        paid(40_000, 0, true);

        assertThat(paymentRepository.sumTotalApprovedAmount()).isEqualByComparingTo("35000");
        assertThat(paymentRepository.sumApprovedAmountAfter(LocalDateTime.now().minusDays(1)))
                .isEqualByComparingTo("35000");
        assertThat(paymentRepository.findDailyRevenueSince(LocalDateTime.now().minusDays(1)))
                .singleElement()
                .satisfies(d -> assertThat(d.getRevenue()).isEqualByComparingTo("35000"));
    }

    @Test
    @DisplayName("PG 관리 화면에서 전액 취소하면 웹훅으로 반영돼 매출에서 빠진다")
    void consoleCancelWebhookRemovesRevenue() {
        Payment payment = paid(50_000, 0, false);

        paymentService.handleWebhook("NICEPAY",
                "{\"tid\":\"" + payment.getPgTransactionId() + "\",\"status\":\"cancelled\",\"amount\":50000}");

        Payment reloaded = paymentRepository.findByPaymentNo(payment.getPaymentNo()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("CANCELLED");
        assertThat(orderRepository.findById(reloaded.getOrder().getId()).orElseThrow().getOrderStatus())
                .isEqualTo("CANCELLED");
        assertThat(paymentRepository.sumTotalApprovedAmount()).isEqualByComparingTo("0");
    }

    private Payment paid(long amount, long refunded, boolean orderCancelled) {
        BigDecimal total = BigDecimal.valueOf(amount);
        Order order = Order.builder()
                .orderNo("RV-" + UUID.randomUUID().toString().substring(0, 8))
                .productAmount(total).discountAmount(BigDecimal.ZERO).shippingAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO).totalAmount(total)
                .ordererName("테스트").ordererEmail("t@koala.test").ordererPhone("01000000000")
                .build();
        order.markPaid();
        if (orderCancelled) order.forceCancel();
        orderRepository.save(order);

        Payment payment = Payment.builder()
                .order(order).paymentNo("PAY-" + UUID.randomUUID().toString().substring(0, 8))
                .provider("NICEPAY").method("CARD").requestedAmount(total)
                .build();
        payment.markCaptured("TID-" + UUID.randomUUID(), "A1", total, "{}");
        if (refunded > 0) payment.markCancelled(BigDecimal.valueOf(refunded));
        return paymentRepository.save(payment);
    }
}
