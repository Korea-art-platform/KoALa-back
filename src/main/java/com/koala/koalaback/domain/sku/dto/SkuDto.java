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

import com.koala.koalaback.domain.pricing.VatPolicy;

import java.math.BigDecimal;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.List;

public class SkuDto {
    @Getter @Setter
    public static class CreateRequest {
        @NotBlank
        private String artistCode;

        // 관리자 등록 화면은 상품명(name)과 슬러그(slug)를 받지 않는다.
        // 모델 · 세부모델명 · 색상을 조합해 서버가 만든다.
        //
        // CSV 일괄 등록은 예전부터 두 값을 직접 넣어 왔고 그 파일 양식을 이미
        // 쓰고 있어, 값이 오면 그대로 존중한다. 화면에서는 비어 온다.
        private String name;
        private String slug;

        @NotBlank
        private String model;

        @NotBlank
        private String modelEn;

        @NotBlank
        private String subModelName;

        @NotBlank
        private String subModelNameEn;

        @NotBlank
        private String color;

        @NotBlank
        private String colorEn;

        @NotBlank
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

        @NotNull @PositiveOrZero
        private BigDecimal salePrice;

        private Integer editionSize;
        private Integer editionNumber;
        private String badges;

        // 등록 화면은 상품을 만든 뒤 이미지를 올린다. 그래서 여기서는 비어 온다.
        // 대표 이미지 필수 여부는 등록 화면이 막고, 수정 요청에서는 필수로 받는다.
        private String primaryImageUrl;

        @NotNull @PositiveOrZero
        private BigDecimal widthCm;

        @NotNull @PositiveOrZero
        private BigDecimal heightCm;

        @NotNull @PositiveOrZero
        private BigDecimal depthCm;

        private BigDecimal weightKg;

        @NotNull @PositiveOrZero
        private Integer weightG;
    }

    @Getter @Setter
    public static class UpdateRequest {
        // 이미 등록된 상품은 예전 방식으로 만들어진 이름이 있다.
        // 이름과 슬러그는 그대로 두고, 모델/세부모델명/색상만 고친다.

        @NotBlank
        private String model;

        @NotBlank
        private String modelEn;

        @NotBlank
        private String subModelName;

        @NotBlank
        private String subModelNameEn;

        @NotBlank
        private String color;

        @NotBlank
        private String colorEn;

        @NotBlank
        private String description;

        private String skuType;

        @NotBlank
        private String mainCategory;

        @NotBlank
        private String genre;

        // 재질 · 포장은 선택
        private String material;
        private String materialDescription;
        private String packagingTitle;
        private String packagingDescription;

        @NotNull @PositiveOrZero
        private BigDecimal listPrice;

        @NotNull @PositiveOrZero
        private BigDecimal salePrice;

        // 에디션 번호는 선택
        private Integer editionSize;
        private Integer editionNumber;
        private String badges;

        @NotBlank
        private String primaryImageUrl;

        @NotNull @PositiveOrZero
        private BigDecimal widthCm;

        @NotNull @PositiveOrZero
        private BigDecimal heightCm;

        @NotNull @PositiveOrZero
        private BigDecimal depthCm;

        private BigDecimal weightKg;

        @NotNull @PositiveOrZero
        private Integer weightG;
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
        // 카드에는 모델만 큰 제목으로 쓴다. 세부모델명과 색상은 상세에서 보여준다.
        private String model;
        private String slug;
        private String skuType;
        private String mainCategory;
        private String genre;
        private BigDecimal listPrice;
        private BigDecimal salePrice;
        private BigDecimal effectivePrice;

        /**
         * 화면에 보이고 실제로 결제되는 금액 — 공급가액 + 부가세.
         *
         * listPrice·salePrice 는 부가세를 뺀 공급가액이고 어드민이 고치는 값이다.
         * 고객에게 보여줄 때는 반드시 이 값을 쓴다. 표시가와 결제가가 다르면 안 된다.
         */
        private BigDecimal displayPrice;

        /** 정가에 부가세를 더한 금액. 할인 표시의 취소선에 쓴다. */
        private BigDecimal displayListPrice;

        /** 면세 상품인가 — 원작에는 부가세를 붙이지 않는다. */
        private Boolean taxExempt;
        private Boolean isLimitedEdition;
        private String description;
        private String primaryImageUrl;
        private String status;
        private String artistName;
        private String artistCode;
        private Integer stockQuantity;
        private BigDecimal avgRating;
        private Integer reviewCount;

        public static SummaryResponse from(Sku sku, int stock, SkuReviewStats stats,
                                           VatPolicy vat, Set<String> exempt) {
            return SummaryResponse.builder()
                    .id(sku.getId())
                    .skuCode(sku.getSkuCode())
                    .name(sku.getName())
                    .model(sku.getModel())
                    .slug(sku.getSlug())
                    .skuType(sku.getSkuType())
                    .mainCategory(sku.getMainCategory())
                    .genre(sku.getGenre())
                    .listPrice(sku.getListPrice())
                    .salePrice(sku.getSalePrice())
                    .effectivePrice(sku.getEffectivePrice())
                    .displayPrice(vat.grossOf(sku.getEffectivePrice(), sku.getMainCategory(), exempt))
                    .displayListPrice(vat.grossOf(sku.getListPrice(), sku.getMainCategory(), exempt))
                    .taxExempt(vat.isExempt(sku.getMainCategory(), exempt))
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
        private String model;
        private String subModelName;
        private String modelEn;
        private String subModelNameEn;
        private String color;
        private String colorEn;
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

        /**
         * 화면에 보이고 실제로 결제되는 금액 — 공급가액 + 부가세.
         *
         * listPrice·salePrice 는 부가세를 뺀 공급가액이고 어드민이 고치는 값이다.
         * 고객에게 보여줄 때는 반드시 이 값을 쓴다. 표시가와 결제가가 다르면 안 된다.
         */
        private BigDecimal displayPrice;

        /** 정가에 부가세를 더한 금액. 할인 표시의 취소선에 쓴다. */
        private BigDecimal displayListPrice;

        /** 면세 상품인가 — 원작에는 부가세를 붙이지 않는다. */
        private Boolean taxExempt;
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
        private Integer weightG;
        private String status;
        private LocalDateTime publishedAt;
        private String artistCode;
        private String artistName;
        private Integer stockQuantity;
        private BigDecimal avgRating;
        private Integer reviewCount;
        private List<MediaResponse> mediaList;

        public static DetailResponse from(VatPolicy vat, Set<String> exempt, Sku sku, int stock,
                                          SkuReviewStats stats, List<SkuMedia> media) {
            return DetailResponse.builder()
                    .id(sku.getId())
                    .skuCode(sku.getSkuCode())
                    .name(sku.getName())
                    .model(sku.getModel())
                    .subModelName(sku.getSubModelName())
                    .modelEn(sku.getModelEn())
                    .subModelNameEn(sku.getSubModelNameEn())
                    .color(sku.getColor())
                    .colorEn(sku.getColorEn())
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
                    .displayPrice(vat.grossOf(sku.getEffectivePrice(), sku.getMainCategory(), exempt))
                    .displayListPrice(vat.grossOf(sku.getListPrice(), sku.getMainCategory(), exempt))
                    .taxExempt(vat.isExempt(sku.getMainCategory(), exempt))
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
                    .weightG(sku.getWeightG())
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
