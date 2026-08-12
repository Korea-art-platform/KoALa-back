package com.koala.koalaback.infra.slack;

import com.koala.koalaback.domain.order.event.OrderCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("관리자 주문 알림 메시지")
class AdminOrderNotifierTest {

    @SuppressWarnings("unchecked")
    private final AdminOrderNotifier notifier =
            new AdminOrderNotifier(mock(ObjectProvider.class));

    @Test
    @DisplayName("금액·주문번호·주문자·상품이 모두 들어간다")
    void containsEverythingNeededToShip() {
        String message = notifier.buildMessage(event(
                new OrderCompletedEvent.Item("SKU-1", "푸른 곰", "김작가", 2, new BigDecimal("300000"))));

        assertThat(message)
                .contains("450,000원")      // 총액 — 알림 목록 첫 줄
                .contains("ORD-20260812-1")
                .contains("홍길동")
                .contains("푸른 곰")
                .contains("김작가")         // 발송 주체가 작가별로 갈린다
                .contains("× 2");
    }

    @Test
    @DisplayName("작가명이 없어도 메시지를 만든다 — 옛 이벤트에는 이 필드가 없다")
    void artistNameIsOptional() {
        String message = notifier.buildMessage(event(
                new OrderCompletedEvent.Item("SKU-1", "푸른 곰", null, 1, new BigDecimal("150000"))));

        assertThat(message).contains("푸른 곰").doesNotContain("[]");
    }

    private OrderCompletedEvent event(OrderCompletedEvent.Item... items) {
        return OrderCompletedEvent.of(
                1L, "ORD-20260812-1", 10L,
                "홍길동", "test@example.com",
                new BigDecimal("450000"), new BigDecimal("0"), new BigDecimal("450000"),
                List.of(items));
    }
}
