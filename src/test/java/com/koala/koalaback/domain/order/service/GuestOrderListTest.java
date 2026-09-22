package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.order.dto.OrderDto;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.global.crypto.PiiIndex;
import com.koala.koalaback.global.util.PhoneNormalizer;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("비회원 주문 목록 조회")
@TestPropertySource(properties = "pii.encryption.key=a29hbGEtdGVzdC1rZXktZm9yLWd1ZXN0LWxvb2t1cCE=")
class GuestOrderListTest extends IntegrationTestSupport {
    private static final String PREFIX = "GUESTLIST";
    private static final String EMAIL = "guest-list@test.local";
    private static final String PHONE = "01055667788";

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PiiIndex piiIndex;
    @Autowired private PhoneNormalizer phoneNormalizer;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM orders WHERE order_no LIKE ?", PREFIX + "%");
    }

    private Order saveGuestOrder(String suffix, String email, String rawPhone) {
        String phone = phoneNormalizer.normalize(rawPhone);
        Order order = Order.builder()
                .orderNo(PREFIX + "-" + suffix)
                .user(null)
                .productAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .shippingAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(10000))
                .ordererName("손님")
                .ordererEmail(email)
                .ordererPhone(phone)
                .build();
        order.applyOrdererIndex(piiIndex.ofEmail(email), piiIndex.ofPhone(phone), piiIndex.last4Of(phone));
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("이메일과 휴대폰이 모두 맞으면 최근 주문부터 돌려준다")
    void returnsOwnOrders() {
        saveGuestOrder("A", EMAIL, PHONE);
        saveGuestOrder("B", EMAIL, PHONE);

        List<OrderDto.OrderSummaryResponse> found = orderService.getGuestOrders(EMAIL, PHONE);

        assertThat(found).extracting(OrderDto.OrderSummaryResponse::getOrderNo)
                .contains(PREFIX + "-A", PREFIX + "-B");
    }

    @Test
    @DisplayName("휴대폰번호가 다르면 한 건도 돌려주지 않는다")
    void wrongPhoneReturnsNothing() {
        saveGuestOrder("C", EMAIL, PHONE);

        assertThat(orderService.getGuestOrders(EMAIL, "01099998888")).isEmpty();
    }

    @Test
    @DisplayName("이메일이 다르면 한 건도 돌려주지 않는다")
    void wrongEmailReturnsNothing() {
        saveGuestOrder("D", EMAIL, PHONE);

        assertThat(orderService.getGuestOrders("someone-else@test.local", PHONE)).isEmpty();
    }

    @Test
    @DisplayName("하이픈이 섞인 번호로도 찾는다")
    void normalizesPhone() {
        saveGuestOrder("E", EMAIL, PHONE);

        assertThat(orderService.getGuestOrders(EMAIL, "010-5566-7788"))
                .extracting(OrderDto.OrderSummaryResponse::getOrderNo)
                .contains(PREFIX + "-E");
    }
}
