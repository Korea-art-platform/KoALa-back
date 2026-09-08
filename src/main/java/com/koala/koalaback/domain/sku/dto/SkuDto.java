package com.koala.koalaback.domain.sku.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(name = "SkuCreateRequest")
    public static class CreateRequest {
        @NotBlank
        @Schema(description = "작가 코드", requiredMode = Schema.RequiredMode.REQUIRED)
        private String artistCode;

        // 관리자 등록 화면은 상품명(name)과 슬러그(slug)를 받지 않는다.
        // 모델 · 세부모델명 · 색상을 조합해 서버가 만든다.
        //
        // CSV 일괄 등록은 예전부터 두 값을 직접 넣어 왔고 그 파일 양식을 이미
        // 쓰고 있어, 값이 오면 그대로 존중한다. 화면에서는 비어 온다.
        @Schema(description = "상품명. 화면에서는 비워 보낸다 — 모델·세부모델명·색상으로 서버가 만든다. "
                + "CSV 일괄 등록만 값을 직접 넘긴다")
        private String name;

        @Schema(description = "URL 슬러그. 비우면 영문 모델명으로 서버가 만든다. 겹치면 뒤에 번호가 붙는다")
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
        @Schema(description = "정가. 부가세를 뺀 공급가액이다. 고객 화면에는 여기에 10% 를 더한 값이 보인다",
                example = "300000", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal listPrice;

        @NotNull @PositiveOrZero
        @Schema(description = "판매가. 공급가액이다. 정가보다 클 수 없다(같은 값은 할인 없음)",
                example = "300000", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal salePrice;

        @Schema(description = "에디션 총 수량. 한정판에 쓴다", example = "50")
        private Integer editionSize;

        @Schema(description = "이 작품이 몇 번째인지", example = "7")
        private Integer editionNumber;

        private String badges;

        // 등록 화면은 상품을 만든 뒤 이미지를 올린다. 그래서 여기서는 비어 온다.
        // 대표 이미지 필수 여부는 등록 화면이 막고, 수정 요청에서는 필수로 받는다.
        private String primaryImageUrl;

        @NotNull @PositiveOrZero
        @Schema(description = "가로 (cm)", example = "20.0", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal widthCm;

        @NotNull @PositiveOrZero
        @Schema(description = "세로 (cm)", example = "30.0", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal heightCm;

        @NotNull @PositiveOrZero
        @Schema(description = "높이 (cm)", example = "15.0", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal depthCm;

        @Schema(description = "무게 (kg)", example = "1.2")
        private BigDecimal weightKg;

        @NotNull @PositiveOrZero
        @Schema(description = "무게 (g)", example = "1200", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer weightG;
    }

    @Getter @Setter
    @Schema(name = "SkuUpdateRequest")
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
        @Schema(description = "정가. 부가세를 뺀 공급가액이다. 고객 화면에는 여기에 10% 를 더한 값이 보인다",
                example = "300000", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal listPrice;

        @NotNull @PositiveOrZero
        @Schema(description = "판매가. 공급가액이다. 정가보다 클 수 없다(같은 값은 할인 없음)",
                example = "300000", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal salePrice;

        // 에디션 번호는 선택
        @Schema(description = "에디션 총 수량. 한정판에 쓴다", example = "50")
        private Integer editionSize;

        @Schema(description = "이 작품이 몇 번째인지", example = "7")
        private Integer editionNumber;

        private String badges;

        @NotBlank
        private String primaryImageUrl;

        @NotNull @PositiveOrZero
        @Schema(description = "가로 (cm)", example = "20.0", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal widthCm;

        @NotNull @PositiveOrZero
        @Schema(description = "세로 (cm)", example = "30.0", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal heightCm;

        @NotNull @PositiveOrZero
        @Schema(description = "높이 (cm)", example = "15.0", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal depthCm;

        @Schema(description = "무게 (kg)", example = "1.2")
        private BigDecimal weightKg;

        @NotNull @PositiveOrZero
        @Schema(description = "무게 (g)", example = "1200", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer weightG;
    }

    @Getter
    @Schema(name = "SkuMediaAddRequest")
    public static class MediaAddRequest {
        @NotBlank
        @Schema(description = "미디어 종류", example = "IMAGE", requiredMode = Schema.RequiredMode.REQUIRED)
        private String mediaType;

        @NotBlank
        @Schema(description = "쓰임. 대표 이미지인지 상세 이미지인지 등",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String mediaRole;

        @Schema(description = "대체 텍스트. 화면 낭독기가 읽는다")
        private String altText;

        @Schema(description = "정렬 순서. 작을수록 앞", example = "0")
        private Integer sortOrder;

        @Schema(description = "대표 이미지 여부", example = "false")
        private Boolean isPrimary;
    }

    @Getter
    @Schema(name = "SkuFrameUploadItem", description = "360도 회전 프레임 한 장")
    public static class FrameUploadItem {
        @NotBlank
        @Schema(description = "이미 올라간 파일의 주소", requiredMode = Schema.RequiredMode.REQUIRED)
        private String fileUrl;

        private String thumbnailUrl;

        @Schema(description = "각도. 0 이상 360 미만이어야 한다 — 360 을 허용하면 0 과 같은 자리에 "
                + "두 장이 겹친다", example = "45.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        private BigDecimal angleDegree;
    }

    @Getter
    @Builder
    @Schema(name = "SkuSummaryResponse", description = "목록·카드에 쓰는 요약")
    public static class SummaryResponse {
        private Long id;

        @Schema(example = "A1B2C3D4E5F60718")
        private String skuCode;

        @Schema(description = "상품명. 모델·세부모델명·색상을 합쳐 만든 값이다")
        private String name;

        // 카드에는 모델만 큰 제목으로 쓴다. 세부모델명과 색상은 상세에서 보여준다.
        @Schema(description = "모델명. 카드에는 이것만 큰 제목으로 쓴다")
        private String model;

        @Schema(description = "URL 슬러그")
        private String slug;

        @Schema(defaultValue = "ARTWORK")
        private String skuType;

        @Schema(description = "대분류 코드. 표시 이름은 분류 API 에서 받는다")
        private String mainCategory;

        @Schema(description = "소분류 코드")
        private String genre;
        @Schema(description = "정가. 부가세를 뺀 공급가액이다. 고객 화면에 그대로 쓰면 안 된다",
                example = "300000")
        private BigDecimal listPrice;

        @Schema(description = "판매가. 부가세를 뺀 공급가액이다", example = "300000")
        private BigDecimal salePrice;

        @Schema(description = "실제 적용되는 공급가액. 할인이 있으면 판매가, 없으면 정가", example = "300000")
        private BigDecimal effectivePrice;

        /**
         * 화면에 보이고 실제로 결제되는 금액 — 공급가액 + 부가세.
         *
         * listPrice·salePrice 는 부가세를 뺀 공급가액이고 어드민이 고치는 값이다.
         * 고객에게 보여줄 때는 반드시 이 값을 쓴다. 표시가와 결제가가 다르면 안 된다.
         */
        @Schema(description = "고객에게 보여주고 실제로 청구되는 금액 — 공급가액 + 부가세. "
                + "화면에는 반드시 이 값을 쓴다. 표시가와 결제가가 다르면 안 된다",
                example = "330000")
        private BigDecimal displayPrice;

        /** 정가에 부가세를 더한 금액. 할인 표시의 취소선에 쓴다. */
        @Schema(description = "정가에 부가세를 더한 금액. 할인 표시의 취소선에 쓴다", example = "330000")
        private BigDecimal displayListPrice;

        /** 면세 상품인가 — 원작에는 부가세를 붙이지 않는다. */
        @Schema(description = "면세 상품인지. 원작 분류에는 부가세를 붙이지 않아 "
                + "displayPrice 가 공급가액과 같다", example = "false")
        private Boolean taxExempt;
        @Schema(description = "한정판인지", example = "true")
        private Boolean isLimitedEdition;

        private String description;

        @Schema(description = "대표 이미지 주소")
        private String primaryImageUrl;

        @Schema(description = "상태. DRAFT(미공개) · ACTIVE(판매중) · OUT_OF_STOCK(품절) · DISCONTINUED(단종)", example = "ACTIVE")
        private String status;

        private String artistName;
        private String artistCode;

        @Schema(description = "현재 재고. 장부의 증감 합계다", example = "12")
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
    @Schema(name = "SkuDetailResponse", description = "작품 상세")
    public static class DetailResponse {
        private Long id;

        @Schema(example = "A1B2C3D4E5F60718")
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
        @Schema(description = "정가. 부가세를 뺀 공급가액이다. 고객 화면에 그대로 쓰면 안 된다",
                example = "300000")
        private BigDecimal listPrice;

        @Schema(description = "판매가. 부가세를 뺀 공급가액이다", example = "300000")
        private BigDecimal salePrice;

        @Schema(description = "실제 적용되는 공급가액. 할인이 있으면 판매가, 없으면 정가", example = "300000")
        private BigDecimal effectivePrice;

        /**
         * 화면에 보이고 실제로 결제되는 금액 — 공급가액 + 부가세.
         *
         * listPrice·salePrice 는 부가세를 뺀 공급가액이고 어드민이 고치는 값이다.
         * 고객에게 보여줄 때는 반드시 이 값을 쓴다. 표시가와 결제가가 다르면 안 된다.
         */
        @Schema(description = "고객에게 보여주고 실제로 청구되는 금액 — 공급가액 + 부가세. "
                + "화면에는 반드시 이 값을 쓴다. 표시가와 결제가가 다르면 안 된다",
                example = "330000")
        private BigDecimal displayPrice;

        /** 정가에 부가세를 더한 금액. 할인 표시의 취소선에 쓴다. */
        @Schema(description = "정가에 부가세를 더한 금액. 할인 표시의 취소선에 쓴다", example = "330000")
        private BigDecimal displayListPrice;

        /** 면세 상품인가 — 원작에는 부가세를 붙이지 않는다. */
        @Schema(description = "면세 상품인지. 원작 분류에는 부가세를 붙이지 않아 "
                + "displayPrice 가 공급가액과 같다", example = "false")
        private Boolean taxExempt;
        @Schema(description = "한정판인지", example = "true")
        private Boolean isLimitedEdition;

        @Schema(description = "에디션 총 수량", example = "50")
        private Integer editionSize;

        @Schema(description = "이 작품이 몇 번째인지", example = "7")
        private Integer editionNumber;

        private String badges;
        private String primaryImageUrl;

        @Schema(description = "AR 로 볼 수 있는 3D 파일 주소. 없으면 null")
        private String arAssetUrl;

        private String arPreviewImageUrl;

        @Schema(description = "가로 (cm)", example = "20.0")
        private BigDecimal widthCm;

        @Schema(description = "세로 (cm)", example = "30.0")
        private BigDecimal heightCm;

        @Schema(description = "높이 (cm)", example = "15.0")
        private BigDecimal depthCm;

        @Schema(description = "무게 (kg)", example = "1.2")
        private BigDecimal weightKg;

        @Schema(description = "무게 (g)", example = "1200")
        private Integer weightG;

        @Schema(description = "상태. DRAFT(미공개) · ACTIVE(판매중) · OUT_OF_STOCK(품절) · DISCONTINUED(단종)", example = "ACTIVE")
        private String status;

        @Schema(description = "공개된 시각. 공개 전에는 null")
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
    @Schema(name = "SkuMediaResponse",
            description = "작품 이미지 한 장. 360도 회전 프레임도 같은 형태로 내려간다")
    public static class MediaResponse {
        private Long id;

        @Schema(example = "IMAGE")
        private String mediaType;

        @Schema(description = "쓰임. 대표 이미지인지 상세 이미지인지 등")
        private String mediaRole;

        private String fileUrl;
        private String thumbnailUrl;

        @Schema(description = "대체 텍스트. 화면 낭독기가 읽는다")
        private String altText;

        @Schema(description = "정렬 순서. 작을수록 앞", example = "0")
        private Integer sortOrder;

        @Schema(description = "360도 프레임일 때의 각도. 아니면 null", example = "45.0")
        private BigDecimal angleDegree;

        @Schema(description = "대표 이미지 여부", example = "false")
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
    @Schema(name = "SkuFrameListResponse", description = "360도 회전 프레임 목록. 각도 오름차순")
    public static class FrameListResponse {
        @Schema(example = "A1B2C3D4E5F60718")
        private String skuCode;

        @Schema(description = "프레임 수", example = "36")
        private int frameCount;

        private List<MediaResponse> frames;
    }

    @Getter
    @Builder
    @Schema(name = "SkuStockResponse", description = "재고 수량")
    public static class StockResponse {
        @Schema(example = "A1B2C3D4E5F60718")
        private String skuCode;

        @Schema(description = "현재 재고. 장부(sku_stock_ledger)의 증감 합계다", example = "12")
        private int stockQuantity;
    }
}
