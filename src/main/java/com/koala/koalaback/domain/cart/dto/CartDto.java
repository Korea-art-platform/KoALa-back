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

        /**
         * 아직 아무것도 담지 않은 사람에게 줄 응답.
         *
         * 장바구니를 만들지 않고 빈 것을 돌려준다. 화면은 "담긴 것이 없다"만
         * 보여주면 되고, 그러려고 행을 하나 만들 이유가 없다.
         */
        public static CartResponse empty() {
            return CartResponse.builder()
                    .currency("KRW")
                    .items(List.of())
                    .subtotalAmount(BigDecimal.ZERO)
                    .supplyAmount(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .totalItemCount(0)
                    .build();
        }

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
