package com.koala.koalaback.domain.wishlist.dto;

import com.koala.koalaback.domain.wishlist.entity.WishlistItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WishlistDto {
    @Getter
    @Builder
    public static class WishlistItemResponse {
        private Long id;
        private String skuCode;
        private String skuName;
        private String primaryImageUrl;
        private BigDecimal effectivePrice;
        private String status;
        private String artistName;
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
