package com.koala.koalaback.domain.cart.service;

import com.koala.koalaback.domain.cart.dto.CartDto;
import com.koala.koalaback.domain.cart.entity.Cart;
import com.koala.koalaback.domain.cart.entity.CartItem;
import com.koala.koalaback.domain.cart.repository.CartItemRepository;
import com.koala.koalaback.domain.cart.repository.CartRepository;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.domain.sku.service.StockService;
import com.koala.koalaback.domain.user.service.UserService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import com.koala.koalaback.domain.pricing.VatPolicy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    @InjectMocks
    private CartService cartService;

    @Mock private CartRepository cartRepository;
    // 부가세 계산은 순수 함수라 흉내 내면 오히려 틀린 것을 통과시킨다.
    // 면세 분류를 읽는 부분만 비워 두고 진짜를 쓴다.
    @Spy private VatPolicy vatPolicy = new VatPolicy(null) {
        @Override public java.util.Set<String> exemptMainCategories() { return java.util.Set.of(); }
    };
    @Mock private CartItemRepository cartItemRepository;
    @Mock private UserService userService;
    @Mock private SkuService skuService;
    @Mock private StockService stockService;

    @Test
    @DisplayName("장바구니 상품 추가 성공")
    void addItem_success() {
        Long userId = 1L;
        CartDto.AddItemRequest req = mock(CartDto.AddItemRequest.class);
        given(req.getSkuCode()).willReturn("SKU-001");
        given(req.getQuantity()).willReturn(2);

        Sku sku = mock(Sku.class);
        given(sku.getId()).willReturn(1L);
        given(sku.isAvailable()).willReturn(true);
        given(sku.getEffectivePrice()).willReturn(new BigDecimal("10000"));

        Cart cart = Cart.builder().user(mock(com.koala.koalaback.domain.user.entity.User.class)).build();

        given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart));
        given(skuService.getSkuEntityByCode("SKU-001")).willReturn(sku);
        given(stockService.getStock(1L)).willReturn(10);
        given(cartItemRepository.findByCartIdAndSkuId(any(), any())).willReturn(Optional.empty());
        given(cartItemRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CartDto.CartResponse result = cartService.addItem(userId, req);

        assertThat(result).isNotNull();
        then(cartItemRepository).should().save(any(CartItem.class));
    }

    @Test
    @DisplayName("장바구니 상품 추가 실패 — 재고 부족")
    void addItem_fail_out_of_stock() {
        Long userId = 1L;
        CartDto.AddItemRequest req = mock(CartDto.AddItemRequest.class);
        given(req.getSkuCode()).willReturn("SKU-001");
        given(req.getQuantity()).willReturn(10);

        Sku sku = mock(Sku.class);
        given(sku.getId()).willReturn(1L);
        given(sku.isAvailable()).willReturn(true);

        Cart cart = Cart.builder().user(mock(com.koala.koalaback.domain.user.entity.User.class)).build();

        given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart));
        given(skuService.getSkuEntityByCode("SKU-001")).willReturn(sku);
        given(stockService.getStock(1L)).willReturn(3);

        assertThatThrownBy(() -> cartService.addItem(userId, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SKU_OUT_OF_STOCK));
    }
}
