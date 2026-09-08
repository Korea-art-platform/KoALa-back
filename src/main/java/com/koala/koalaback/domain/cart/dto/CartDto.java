package com.koala.koalaback.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "장바구니 담기 요청. 이미 담긴 상품이면 새 줄을 만들지 않고 수량을 더한다")
    public static class AddItemRequest {
        @NotBlank
        @Schema(example = "A1B2C3D4E5F60718", requiredMode = Schema.RequiredMode.REQUIRED)
        private String skuCode;

        @NotNull @Min(1)
        @Schema(description = "담을 수량. 재고보다 많으면 거절한다", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer quantity;
    }

    @Getter
    @Schema(description = "수량 변경 요청")
    public static class UpdateItemRequest {
        @NotNull @Min(1)
        @Schema(description = "바꿀 수량. 더하는 것이 아니라 이 값으로 맞춘다. 재고보다 많으면 거절한다",
                example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer quantity;
    }

    @Getter
    @Builder
    @Schema(description = "장바구니 전체. 담기·수량변경·삭제 모두 이 형태로 답한다")
    public static class CartResponse {
        @Schema(description = "장바구니 id. 아직 만들어지지 않았으면 null")
        private Long cartId;

        @Schema(example = "KRW")
        private String currency;

        private List<CartItemResponse> items;

        /** 고객이 내는 금액 합계 — 부가세 포함 */
        @Schema(description = "고객이 내는 금액 합계 — 부가세 포함. 화면에 찍히는 숫자다. "
                + "배송비는 들어 있지 않다", example = "363000")
        private BigDecimal subtotalAmount;

        /** 그중 공급가액 */
        @Schema(description = "그중 공급가액", example = "330000")
        private BigDecimal supplyAmount;

        /** 그중 부가세 */
        @Schema(description = "그중 부가세. 면세 분류(원작)에는 붙지 않는다", example = "33000")
        private BigDecimal taxAmount;

        @Schema(description = "담긴 수량의 합계. 줄 수가 아니다", example = "3")
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
    @Schema(description = "장바구니 한 줄")
    public static class CartItemResponse {
        @Schema(description = "항목 id. 수량 변경·삭제에 쓴다")
        private Long id;

        @Schema(example = "A1B2C3D4E5F60718")
        private String skuCode;

        private String skuName;
        private String primaryImageUrl;

        @Schema(example = "2")
        private Integer quantity;

        /** 화면에 보이는 단가 — 부가세 포함 */
        @Schema(description = "화면에 보이는 단가 — 부가세 포함", example = "181500")
        private BigDecimal unitPrice;

        /** 단가 × 수량 — 부가세 포함 */
        @Schema(description = "단가 × 수량 — 부가세 포함", example = "363000")
        private BigDecimal lineAmount;

        @Schema(description = "그중 공급가액", example = "330000")
        private BigDecimal supplyAmount;

        @Schema(description = "그중 부가세", example = "33000")
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
