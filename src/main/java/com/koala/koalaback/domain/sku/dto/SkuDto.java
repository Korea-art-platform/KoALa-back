package com.koala.koalaback.domain.sku.dto;

import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.entity.SkuMedia;
import com.koala.koalaback.domain.sku.entity.SkuReviewStats;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class SkuDto {
    @Getter @Setter
    public static class CreateRequest {
        @NotBlank
        private String artistCode;

        @NotBlank
        private String name;

        @NotBlank
        private String slug;

        private String description;
        private String skuType;

        @NotBlank
        private String mainCategory;

        @NotBlank
        private String genre;

        private String material;
        private String materialDescription;
        private String packagingTitle;
        private String packagingDescription;

        @NotNull @PositiveOrZero
        private BigDecimal listPrice;

        private BigDecimal salePrice;
        private Integer editionSize;
        private Integer editionNumber;
        private String badges;
        private String primaryImageUrl;
        private BigDecimal widthCm;
        private BigDecimal heightCm;
        private BigDecimal depthCm;
        private BigDecimal weightKg;
    }

    @Getter @Setter
    public static class UpdateRequest {
        @NotBlank
        private String name;

        @NotBlank
        private String slug;

        private String description;
        private String skuType;

        @NotBlank
        private String mainCategory;

        @NotBlank
        private String genre;

        private String material;
        private String materialDescription;
        private String packagingTitle;
        private String packagingDescription;

        @NotNull @PositiveOrZero
        private BigDecimal listPrice;

        private BigDecimal salePrice;
        private Integer editionSize;
        private Integer editionNumber;
        private String badges;
        private String primaryImageUrl;
    }

    @Getter
    public static class MediaAddRequest {
        @NotBlank
        private String mediaType;

        @NotBlank
        private String mediaRole;

        private String altText;
        private Integer sortOrder;
        private Boolean isPrimary;
    }

    @Getter
    public static class FrameUploadItem {
        @NotBlank
        private String fileUrl;

        private String thumbnailUrl;

        @NotNull
        private BigDecimal angleDegree;
    }

    @Getter
    @Builder
    public static class SummaryResponse {
        private Long id;
        private String skuCode;
        private String name;
        private String slug;
        private String skuType;
        private String mainCategory;
        private String genre;
        private BigDecimal listPrice;
        private BigDecimal salePrice;
        private BigDecimal effectivePrice;
        private Boolean isLimitedEdition;
        private String description;
        private String primaryImageUrl;
        private String status;
        private String artistName;
        private String artistCode;
        private Integer stockQuantity;
        private BigDecimal avgRating;
        private Integer reviewCount;

        public static SummaryResponse from(Sku sku, int stock, SkuReviewStats stats) {
            return SummaryResponse.builder()
                    .id(sku.getId())
                    .skuCode(sku.getSkuCode())
                    .name(sku.getName())
                    .slug(sku.getSlug())
                    .skuType(sku.getSkuType())
                    .mainCategory(sku.getMainCategory())
                    .genre(sku.getGenre())
                    .listPrice(sku.getListPrice())
                    .salePrice(sku.getSalePrice())
                    .effectivePrice(sku.getEffectivePrice())
                    .isLimitedEdition(sku.getIsLimitedEdition())
                    .description(sku.getDescription())
                    .primaryImageUrl(sku.getPrimaryImageUrl())
                    .status(sku.getStatus())
                    .artistName(sku.getArtist().getName())
                    .artistCode(sku.getArtist().getArtistCode())
                    .stockQuantity(stock)
                    .avgRating(stats != null ? stats.getAvgRating() : BigDecimal.ZERO)
                    .reviewCount(stats != null ? stats.getReviewCount() : 0)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class DetailResponse {
        private Long id;
        private String skuCode;
        private String name;
        private String slug;
        private String description;
        private String skuType;
        private String mainCategory;
        private String genre;
        private String material;
        private String materialDescription;
        private String packagingTitle;
        private String packagingDescription;
        private String currency;
        private BigDecimal listPrice;
        private BigDecimal salePrice;
        private BigDecimal effectivePrice;
        private Boolean isLimitedEdition;
        private Integer editionSize;
        private Integer editionNumber;
        private String badges;
        private String primaryImageUrl;
        private String arAssetUrl;
        private String arPreviewImageUrl;
        private BigDecimal widthCm;
        private BigDecimal heightCm;
        private BigDecimal depthCm;
        private BigDecimal weightKg;
        private String status;
        private LocalDateTime publishedAt;
        private String artistCode;
        private String artistName;
        private Integer stockQuantity;
        private BigDecimal avgRating;
        private Integer reviewCount;
        private List<MediaResponse> mediaList;

        public static DetailResponse from(Sku sku, int stock,
                                          SkuReviewStats stats, List<SkuMedia> media) {
            return DetailResponse.builder()
                    .id(sku.getId())
                    .skuCode(sku.getSkuCode())
                    .name(sku.getName())
                    .slug(sku.getSlug())
                    .description(sku.getDescription())
                    .skuType(sku.getSkuType())
                    .mainCategory(sku.getMainCategory())
                    .genre(sku.getGenre())
                    .material(sku.getMaterial())
                    .materialDescription(sku.getMaterialDescription())
                    .packagingTitle(sku.getPackagingTitle())
                    .packagingDescription(sku.getPackagingDescription())
                    .currency(sku.getCurrency())
                    .listPrice(sku.getListPrice())
                    .salePrice(sku.getSalePrice())
                    .effectivePrice(sku.getEffectivePrice())
                    .isLimitedEdition(sku.getIsLimitedEdition())
                    .editionSize(sku.getEditionSize())
                    .editionNumber(sku.getEditionNumber())
                    .badges(sku.getBadges())
                    .primaryImageUrl(sku.getPrimaryImageUrl())
                    .arAssetUrl(sku.getArAssetUrl())
                    .arPreviewImageUrl(sku.getArPreviewImageUrl())
                    .widthCm(sku.getWidthCm())
                    .heightCm(sku.getHeightCm())
                    .depthCm(sku.getDepthCm())
                    .weightKg(sku.getWeightKg())
                    .status(sku.getStatus())
                    .publishedAt(sku.getPublishedAt())
                    .artistCode(sku.getArtist().getArtistCode())
                    .artistName(sku.getArtist().getName())
                    .stockQuantity(stock)
                    .avgRating(stats != null ? stats.getAvgRating() : BigDecimal.ZERO)
                    .reviewCount(stats != null ? stats.getReviewCount() : 0)
                    .mediaList(media.stream().map(MediaResponse::from).toList())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class MediaResponse {
        private Long id;
        private String mediaType;
        private String mediaRole;
        private String fileUrl;
        private String thumbnailUrl;
        private String altText;
        private Integer sortOrder;
        private BigDecimal angleDegree;
        private Boolean isPrimary;

        public static MediaResponse from(SkuMedia m) {
            return MediaResponse.builder()
                    .id(m.getId())
                    .mediaType(m.getMediaType())
                    .mediaRole(m.getMediaRole())
                    .fileUrl(m.getFileUrl())
                    .thumbnailUrl(m.getThumbnailUrl())
                    .altText(m.getAltText())
                    .sortOrder(m.getSortOrder())
                    .angleDegree(m.getAngleDegree())
                    .isPrimary(m.getIsPrimary())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class FrameListResponse {
        private String skuCode;
        private int frameCount;
        private List<MediaResponse> frames;
    }

    @Getter
    @Builder
    public static class StockResponse {
        private String skuCode;
        private int stockQuantity;
    }
}
