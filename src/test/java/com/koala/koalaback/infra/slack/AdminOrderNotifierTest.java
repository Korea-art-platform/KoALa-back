package com.koala.koalaback.infra.slack;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderShipment;
import com.koala.koalaback.domain.order.event.OrderCompletedEvent;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.order.repository.OrderShipmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("관리자 주문 알림 메시지")
class AdminOrderNotifierTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<SlackNotifier> slackProvider = mock(ObjectProvider.class);

    private final AdminOrderNotifier notifier = new AdminOrderNotifier(slackProvider, orders, shipments);

    private static final AdminOrderNotifier.Shipping SEOUL = new AdminOrderNotifier.Shipping(
            "김받는", "+821098765432", "06000", "서울특별시 서초구 테스트로 1", "101호", "문 앞에 놓아주세요");

    @Test
    @DisplayName("정한 항목이 전부 들어간다 — 주문자와 수령인을 따로 적는다")
    void containsEverythingNeededToShip() {
        String message = notifier.buildMessage(event(new BigDecimal("3000"),
                new OrderCompletedEvent.Item("SKU-1", "푸른 곰", "김작가", 2, new BigDecimal("300000")),
                new OrderCompletedEvent.Item("SKU-2", "붉은 곰", "이작가", 1, new BigDecimal("150000"))),
                "+821012345678", SEOUL);

        assertThat(message)
                .contains("주문번호: `ORD-20260812-1`")
                .containsPattern("주문 날짜: \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")
                .contains("주문자: 홍길동")
                .contains("주문자 연락처: test@example.com · 010-1234-5678")
                .contains("수령인: 김받는")
                .contains("수령인 전화번호: 010-9876-5432")
                .contains("주소: (06000) 서울특별시 서초구 테스트로 1 101호")
                .contains("요청사항: 문 앞에 놓아주세요")
                .contains("[김작가] 푸른 곰 × 2")
                .contains("[이작가] 붉은 곰 × 1")
                .contains("수량: 총 3개")
                .contains("금액: 450,000원")
                .contains("배송비: 3,000원")
                .contains("결제 합계: 453,000원");
    }

    @Test
    @DisplayName("주문·수령·상품 순서로 묶인다")
    void sectionsAreInOrder() {
        String message = notifier.buildMessage(event(BigDecimal.ZERO, item()), "+821012345678", SEOUL);

        assertThat(message.indexOf("*주문*")).isLessThan(message.indexOf("*수령*"));
        assertThat(message.indexOf("*수령*")).isLessThan(message.indexOf("*상품*"));
    }

    @Test
    @DisplayName("작가명이 없어도 메시지를 만든다 — 옛 이벤트에는 이 필드가 없다")
    void artistNameIsOptional() {
        String message = notifier.buildMessage(event(BigDecimal.ZERO,
                new OrderCompletedEvent.Item("SKU-1", "푸른 곰", null, 1, new BigDecimal("450000"))),
                null, SEOUL);

        assertThat(message).contains("푸른 곰").doesNotContain("[]");
    }

    @Test
    @DisplayName("상세 주소와 요청사항이 없으면 그만큼만 적는다")
    void optionalShippingFields() {
        String message = notifier.buildMessage(event(BigDecimal.ZERO, item()), null,
                new AdminOrderNotifier.Shipping("김받는", "+821098765432", "06000",
                        "서울특별시 서초구 테스트로 1", null, "  "));

        assertThat(message)
                .contains("주소: (06000) 서울특별시 서초구 테스트로 1\n")
                .contains("요청사항: 없음");
    }

    @Test
    @DisplayName("주문자 전화번호를 못 구하면 이메일만 적는다")
    void contactFallsBackToEmail() {
        String message = notifier.buildMessage(event(BigDecimal.ZERO, item()), null, SEOUL);

        assertThat(message).contains("주문자 연락처: test@example.com\n");
    }

    @Test
    @DisplayName("국제 형식으로 저장된 번호를 국내 표기로 바꿔 보여준다")
    void displaysDomesticPhone() {
        assertThat(AdminOrderNotifier.displayPhone("+821012345678")).isEqualTo("010-1234-5678");
        assertThat(AdminOrderNotifier.displayPhone("+82101234567")).isEqualTo("010-123-4567");
        assertThat(AdminOrderNotifier.displayPhone("+8221234567")).isEqualTo("02-123-4567");
        assertThat(AdminOrderNotifier.displayPhone("+82212345678")).isEqualTo("02-1234-5678");
        assertThat(AdminOrderNotifier.displayPhone("+82311234567")).isEqualTo("031-123-4567");
        assertThat(AdminOrderNotifier.displayPhone("+14155552671")).isEqualTo("+14155552671");
        assertThat(AdminOrderNotifier.displayPhone(null)).isNull();
    }

    @Test
    @DisplayName("주문 ID 로 주문자 전화번호와 배송지를 찾아 싣는다")
    void looksUpContactAndShippingByOrderId() {
        SlackNotifier slack = mock(SlackNotifier.class);
        when(slackProvider.getIfAvailable()).thenReturn(slack);

        Order order = mock(Order.class);
        when(order.getOrdererPhone()).thenReturn("+821012345678");
        when(orders.findById(1L)).thenReturn(Optional.of(order));

        OrderShipment shipment = mock(OrderShipment.class);
        when(shipment.getRecipientName()).thenReturn("김받는");
        when(shipment.getRecipientPhone()).thenReturn("+821098765432");
        when(shipment.getZipCode()).thenReturn("06000");
        when(shipment.getAddress1()).thenReturn("서울특별시 서초구 테스트로 1");
        when(shipment.getAddress2()).thenReturn("101호");
        when(shipment.getDeliveryRequest()).thenReturn("부재 시 경비실");
        when(shipments.findByOrderId(1L)).thenReturn(Optional.of(shipment));

        notifier.notifyOrderCompleted(event(BigDecimal.ZERO, item()));

        verify(slack).send(argThat((String m) ->
                m.contains("주문자 연락처: test@example.com · 010-1234-5678")
                        && m.contains("수령인 전화번호: 010-9876-5432")
                        && m.contains("주소: (06000) 서울특별시 서초구 테스트로 1 101호")
                        && m.contains("요청사항: 부재 시 경비실")));
    }

    @Test
    @DisplayName("배송지를 못 읽어도 알림은 나간다 — 수령 칸만 비운다")
    void sendsEvenWhenShippingLookupFails() {
        SlackNotifier slack = mock(SlackNotifier.class);
        when(slackProvider.getIfAvailable()).thenReturn(slack);
        when(shipments.findByOrderId(anyLong())).thenThrow(new RuntimeException("DB 연결 끊김"));

        notifier.notifyOrderCompleted(event(BigDecimal.ZERO, item()));

        verify(slack).send(argThat((String m) -> m.contains("주문번호: `ORD-20260812-1`")
                && m.contains(AdminOrderNotifier.SHIPPING_UNAVAILABLE)));
    }

    @Test
    @DisplayName("주문자 전화번호를 못 읽어도 알림은 나간다")
    void sendsEvenWhenOrderLookupFails() {
        SlackNotifier slack = mock(SlackNotifier.class);
        when(slackProvider.getIfAvailable()).thenReturn(slack);
        when(orders.findById(anyLong())).thenThrow(new RuntimeException("DB 연결 끊김"));

        notifier.notifyOrderCompleted(event(BigDecimal.ZERO, item()));

        verify(slack).send(argThat((String m) -> m.contains("주문자 연락처: test@example.com\n")));
    }

    private OrderCompletedEvent.Item item() {
        return new OrderCompletedEvent.Item("SKU-1", "푸른 곰", "김작가", 1, new BigDecimal("450000"));
    }

    private OrderCompletedEvent event(BigDecimal shipping, OrderCompletedEvent.Item... items) {
        BigDecimal product = new BigDecimal("450000");
        return OrderCompletedEvent.of(
                1L, "ORD-20260812-1", 10L,
                "홍길동", "test@example.com",
                product, shipping, product.add(shipping),
                List.of(items));
    }
}
