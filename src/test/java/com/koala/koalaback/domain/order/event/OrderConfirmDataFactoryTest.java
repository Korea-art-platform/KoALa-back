package com.koala.koalaback.domain.order.event;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.infra.mail.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("주문 완료 메일 데이터 — 주문자는 DB 에서 읽는다")
class OrderConfirmDataFactoryTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderConfirmDataFactory factory = new OrderConfirmDataFactory(orders);

    @Test
    @DisplayName("주문자 이름·이메일은 주문에서, 금액과 상품은 이벤트에서 옮긴다")
    void mapsFromOrderAndEvent() {
        Order order = mock(Order.class);
        when(order.getOrdererName()).thenReturn("구매자");
        when(order.getOrdererEmail()).thenReturn("buyer@koala.test");
        when(orders.findById(7L)).thenReturn(Optional.of(order));

        EmailService.OrderConfirmData data = factory.from(event());

        assertThat(data.toEmail()).isEqualTo("buyer@koala.test");
        assertThat(data.orderNo()).isEqualTo("ORD-3003");
        assertThat(data.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(53_000));
        assertThat(data.items())
                .extracting(EmailService.OrderConfirmData.ItemData::skuName)
                .containsExactly("테스트 아트토이");
    }

    @Test
    @DisplayName("주문을 찾지 못하면 메일을 만들지 않는다")
    void missingOrder() {
        when(orders.findById(7L)).thenReturn(Optional.empty());

        assertThat(factory.from(event())).isNull();
    }

    private OrderCompletedEvent event() {
        return OrderCompletedEvent.of(
                7L, "ORD-3003", 99L,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(3_000),
                BigDecimal.valueOf(53_000),
                List.of(new OrderCompletedEvent.Item(
                        "SKU-1", "테스트 아트토이", "테스트 작가", 1, BigDecimal.valueOf(50_000))));
    }
}
