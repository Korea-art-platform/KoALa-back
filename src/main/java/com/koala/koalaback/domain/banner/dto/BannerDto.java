package com.koala.koalaback.domain.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.banner.entity.Banner;
import com.koala.koalaback.domain.pricing.VatPolicy;
import com.koala.koalaback.domain.sku.entity.Sku;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

public class BannerDto {
    @Getter
    @Schema(name = "BannerCreateRequest", description = "배너 등록. 노출 기간을 정하면 그 기간에만 고객 화면에 걸린다")
    public static class CreateRequest {
        @NotBlank
        private String bannerType;

        @NotBlank @Size(max = 200)
        private String title;

        @Size(max = 255)
        private String subtitle;

        @Size(max = 100)
        private String badge;

        @Size(max = 500)
        private String description;

        @Size(max = 700)
        private String imageUrl;

        @Size(max = 700)
        private String mobileImageUrl;

        private String videoUrl;

        @Size(max = 40)
        private String skuCode;

        @Size(max = 700)
        private String effectImageUrl1;

        @Size(max = 700)
        private String effectImageUrl2;

        @Size(max = 700)
        private String effectImageUrl3;

        @Size(max = 700)
        private String linkUrl;

        private String linkTarget;

        @Size(max = 30)
        private String bgColor;

        private String textColor;
        private Integer sortOrder;
        private LocalDateTime visibleFrom;
        private LocalDateTime visibleTo;
    }

    @Getter
    @Schema(name = "BannerUpdateRequest", description = "배너 수정")
    public static class UpdateRequest {
        @NotBlank @Size(max = 200)
        private String title;

        @Size(max = 255)
        private String subtitle;

        @Size(max = 100)
        private String badge;

        @Size(max = 500)
        private String description;

        @Size(max = 700)
        private String imageUrl;

        @Size(max = 700)
        private String mobileImageUrl;

        private String videoUrl;

        @Size(max = 40)
        private String skuCode;

        @Size(max = 700)
        private String effectImageUrl1;

        @Size(max = 700)
        private String effectImageUrl2;

        @Size(max = 700)
        private String effectImageUrl3;

        @Size(max = 700)
        private String linkUrl;

        private String linkTarget;

        @Size(max = 30)
        private String bgColor;

        private String textColor;
        private Integer sortOrder;
        private LocalDateTime visibleFrom;
        private LocalDateTime visibleTo;
    }

    @Getter
    @Builder
    @Schema(description = "배너. 고객용 목록은 지금 시각에 걸려 있는 것만 내려가고, 어드민 목록은 기간과 무관하게 전부 내려간다")
    public static class BannerResponse {
        private Long id;
        private String bannerCode;
        private String bannerType;
        private String title;
        private String subtitle;
        private String badge;
        private String description;
        private String imageUrl;
        private String mobileImageUrl;

        private String videoUrl;
        private String skuCode;
        private String skuName;
        private String skuModel;
        private String artistCode;
        private String artistName;
        /** 화면에 보이는 금액 — 공급가액 + 부가세 */
        private BigDecimal displayPrice;
        private BigDecimal displayListPrice;
        private String effectImageUrl1;
        private String effectImageUrl2;
        private String effectImageUrl3;
        private String linkUrl;
        private String linkTarget;
        private String bgColor;
        private String textColor;
        private Integer sortOrder;
        private Boolean isActive;
        private LocalDateTime visibleFrom;
        private LocalDateTime visibleTo;
        private LocalDateTime createdAt;

        public static BannerResponse from(Banner b, VatPolicy vat, Set<String> exempt) {
            // 삭제된 작품은 빼고 내린다
            Sku sku = b.getSku() != null && b.getSku().getDeletedAt() == null ? b.getSku() : null;
            return BannerResponse.builder()
                    .id(b.getId())
                    .bannerCode(b.getBannerCode())
                    .bannerType(b.getBannerType())
                    .title(b.getTitle())
                    .subtitle(b.getSubtitle())
                    .badge(b.getBadge())
                    .description(b.getDescription())
                    .imageUrl(b.getImageUrl())
                    .mobileImageUrl(b.getMobileImageUrl())
                    .videoUrl(b.getVideoUrl())
                    .skuCode(sku != null ? sku.getSkuCode() : null)
                    .skuName(sku != null ? sku.getName() : null)
                    .skuModel(sku != null ? sku.getModel() : null)
                    .artistCode(sku != null ? sku.getArtist().getArtistCode() : null)
                    .artistName(sku != null ? sku.getArtist().getName() : null)
                    .displayPrice(sku != null ? vat.grossOf(sku.getEffectivePrice(), sku.getMainCategory(), exempt) : null)
                    .displayListPrice(sku != null ? vat.grossOf(sku.getListPrice(), sku.getMainCategory(), exempt) : null)
                    .effectImageUrl1(b.getEffectImageUrl1())
                    .effectImageUrl2(b.getEffectImageUrl2())
                    .effectImageUrl3(b.getEffectImageUrl3())
                    .linkUrl(b.getLinkUrl())
                    .linkTarget(b.getLinkTarget())
                    .bgColor(b.getBgColor())
                    .textColor(b.getTextColor())
                    .sortOrder(b.getSortOrder())
                    .isActive(b.getIsActive())
                    .visibleFrom(b.getVisibleFrom())
                    .visibleTo(b.getVisibleTo())
                    .createdAt(b.getCreatedAt())
                    .build();
        }
    }
}
