package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.cart.entity.CartItem;
import com.koala.koalaback.domain.sku.entity.Sku;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("주문 재고 락 획득 순서")
class OrderLockOrderTest {
    @Test
    @DisplayName("장바구니 순서와 무관하게 skuId 오름차순으로 정렬된다")
    void sortByLockOrder_sortsBySkuIdAscending() {
        List<CartItem> items = List.of(cartItemOf(30L), cartItemOf(10L), cartItemOf(20L));

        List<CartItem> sorted = OrderService.sortByLockOrder(items);

        assertThat(sorted).extracting(ci -> ci.getSku().getId())
                .containsExactly(10L, 20L, 30L);
    }

    @Test
    @DisplayName("서로 반대 순서로 담긴 두 주문이 같은 락 순서를 갖는다 — 순환 대기가 생기지 않는다")
    void sortByLockOrder_givesSameOrderForOppositeCarts() {
        List<CartItem> orderA = List.of(cartItemOf(1L), cartItemOf(2L), cartItemOf(3L));
        List<CartItem> orderB = List.of(cartItemOf(3L), cartItemOf(2L), cartItemOf(1L));

        List<Long> lockOrderA = OrderService.sortByLockOrder(orderA)
                .stream().map(ci -> ci.getSku().getId()).toList();
        List<Long> lockOrderB = OrderService.sortByLockOrder(orderB)
                .stream().map(ci -> ci.getSku().getId()).toList();

        assertThat(lockOrderA).isEqualTo(lockOrderB);
        assertThat(lockOrderA).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("원본 리스트의 순서는 바뀌지 않는다 — 금액 계산·주문 아이템 순서에 영향 없음")
    void sortByLockOrder_doesNotMutateInput() {
        List<CartItem> items = List.of(cartItemOf(30L), cartItemOf(10L));

        OrderService.sortByLockOrder(items);

        assertThat(items).extracting(ci -> ci.getSku().getId())
                .containsExactly(30L, 10L);
    }

    private CartItem cartItemOf(Long skuId) {
        Sku sku = mock(Sku.class);
        given(sku.getId()).willReturn(skuId);
        CartItem item = mock(CartItem.class);
        given(item.getSku()).willReturn(sku);
        return item;
    }
}
