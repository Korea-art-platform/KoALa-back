package com.koala.koalaback.domain.cart.dto;

import com.koala.koalaback.domain.cart.entity.Cart;
import com.koala.koalaback.domain.cart.entity.CartItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import com.koala.koalaback.domain.pricing.VatPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

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
        /** 고객이 내는 금액 합계 — 부가세 포함 */
        private BigDecimal subtotalAmount;
        /** 그중 공급가액 */
        private BigDecimal supplyAmount;
        /** 그중 부가세 */
        private BigDecimal taxAmount;
        private int totalItemCount;

        public static CartResponse from(Cart cart, VatPolicy vat, Set<String> exempt) {
            List<CartItemResponse> itemResponses = cart.getItems().stream()
                    .map(item -> CartItemResponse.from(item, vat, exempt))
                    .toList();
            BigDecimal subtotal = itemResponses.stream()
                    .map(CartItemResponse::getLineAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal supply = itemResponses.stream()
                    .map(CartItemResponse::getSupplyAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tax = itemResponses.stream()
                    .map(CartItemResponse::getTaxAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return CartResponse.builder()
                    .cartId(cart.getId())
                    .currency(cart.getCurrency())
                    .items(itemResponses)
                    .subtotalAmount(subtotal)
                    .supplyAmount(supply)
                    .taxAmount(tax)
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
        /** 화면에 보이는 단가 — 부가세 포함 */
        private BigDecimal unitPrice;
        /** 단가 × 수량 — 부가세 포함 */
        private BigDecimal lineAmount;
        private BigDecimal supplyAmount;
        private BigDecimal taxAmount;

        public static CartItemResponse from(CartItem item, VatPolicy vat, Set<String> exempt) {
            VatPolicy.Line line = vat.lineOf(item.getUnitPrice(), item.getQuantity(),
                    item.getSku().getMainCategory(), exempt);
            return CartItemResponse.builder()
                    .id(item.getId())
                    .skuCode(item.getSku().getSkuCode())
                    .skuName(item.getSku().getName())
                    .primaryImageUrl(item.getSku().getPrimaryImageUrl())
                    .quantity(item.getQuantity())
                    .unitPrice(line.unitGross())
                    .lineAmount(line.gross())
                    .supplyAmount(line.supply())
                    .taxAmount(line.tax())
                    .build();
        }
    }
}
