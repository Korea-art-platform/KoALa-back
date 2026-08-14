package com.koala.koalaback.domain.cart.dto;

import com.koala.koalaback.domain.cart.entity.Cart;
import com.koala.koalaback.domain.cart.entity.CartItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

public class CartDto {
    @Getter
    public static class AddItemRequest {
        @NotBlank
        private String skuCode;

        @NotNull @Min(1)
        private Integer quantity;
    }

    @Getter
    public static class UpdateItemRequest {
        @NotNull @Min(1)
        private Integer quantity;
    }

    @Getter
    @Builder
    public static class CartResponse {
        private Long cartId;
        private String currency;
        private List<CartItemResponse> items;
        private BigDecimal subtotalAmount;
        private int totalItemCount;

        public static CartResponse from(Cart cart) {
            List<CartItemResponse> itemResponses = cart.getItems().stream()
                    .map(CartItemResponse::from)
                    .toList();
            BigDecimal subtotal = itemResponses.stream()
                    .map(CartItemResponse::getLineAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return CartResponse.builder()
                    .cartId(cart.getId())
                    .currency(cart.getCurrency())
                    .items(itemResponses)
                    .subtotalAmount(subtotal)
                    .totalItemCount(itemResponses.size())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class CartItemResponse {
        private Long id;
        private String skuCode;
        private String skuName;
        private String primaryImageUrl;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineAmount;

        public static CartItemResponse from(CartItem item) {
            return CartItemResponse.builder()
                    .id(item.getId())
                    .skuCode(item.getSku().getSkuCode())
                    .skuName(item.getSku().getName())
                    .primaryImageUrl(item.getSku().getPrimaryImageUrl())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .lineAmount(item.getLineAmount())
                    .build();
        }
    }
}
