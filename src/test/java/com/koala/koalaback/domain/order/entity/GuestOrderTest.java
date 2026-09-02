package com.koala.koalaback.domain.order.entity;

import com.koala.koalaback.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("비회원 주문")
class GuestOrderTest {

    private Order order(User user) {
        return Order.builder()
                .orderNo("KL-1")
                .user(user)
                .productAmount(BigDecimal.TEN)
                .discountAmount(BigDecimal.ZERO)
                .shippingAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ONE)
                .totalAmount(BigDecimal.TEN)
                .ordererName("홍길동")
                .ordererEmail("a@b.com")
                .ordererPhone("+821000000000")
                .build();
    }

    @Test
    @DisplayName("회원이 없으면 비회원 주문이다")
    void guestWhenNoUser() {
        assertThat(order(null).isGuest()).isTrue();
        assertThat(order(mock(User.class)).isGuest()).isFalse();
    }

    @Test
    @DisplayName("가입하면 계정에 붙는다")
    void attachOnSignup() {
        Order o = order(null);
        User me = mock(User.class);

        o.attachTo(me);

        assertThat(o.isGuest()).isFalse();
        assertThat(o.getUser()).isSameAs(me);
    }

    @Test
    @DisplayName("이미 주인이 있는 주문은 옮겨 붙지 않는다")
    void neverStealsAnothersOrder() {
        // 남의 주문이 옮겨 붙으면 주문 내역에 모르는 주문이 뜨고
        // 배송지·연락처까지 보이게 된다.
        User owner = mock(User.class);
        User other = mock(User.class);
        Order o = order(owner);

        o.attachTo(other);

        assertThat(o.getUser()).isSameAs(owner);
    }
}
