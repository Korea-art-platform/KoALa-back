package com.koala.koalaback.domain.wishlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.wishlist.entity.WishlistItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WishlistDto {
    @Getter
    @Builder
    @Schema(description = "찜한 작품. 최근에 찜한 것이 앞에 온다")
    public static class WishlistItemResponse {
        private Long id;

        @Schema(example = "A1B2C3D4E5F60718")
        private String skuCode;

        private String skuName;
        private String primaryImageUrl;

        @Schema(description = "적용 공급가액. 부가세를 뺀 금액이라 화면에 그대로 쓰면 안 된다",
                example = "300000")
        private BigDecimal effectivePrice;

        @Schema(description = "작품 상태. DRAFT(미공개) · ACTIVE(판매중) · OUT_OF_STOCK(품절) · DISCONTINUED(단종). 찜해 둔 사이 품절·단종이 될 수 있다",
                example = "ACTIVE")
        private String status;

        private String artistName;

        @Schema(description = "찜한 시각")
        private LocalDateTime addedAt;

        public static WishlistItemResponse from(WishlistItem item) {
            return WishlistItemResponse.builder()
                    .id(item.getId())
                    .skuCode(item.getSku().getSkuCode())
                    .skuName(item.getSku().getName())
                    .primaryImageUrl(item.getSku().getPrimaryImageUrl())
                    .effectivePrice(item.getSku().getEffectivePrice())
                    .status(item.getSku().getStatus())
                    .artistName(item.getSku().getArtist().getName())
                    .addedAt(item.getCreatedAt())
                    .build();
        }
    }
}
