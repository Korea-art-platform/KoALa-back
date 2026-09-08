package com.koala.koalaback.domain.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.entity.ArtistCareer;
import com.koala.koalaback.domain.artist.entity.ArtistMedia;
import com.koala.koalaback.domain.sku.entity.Sku;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import com.koala.koalaback.domain.pricing.VatPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public class ArtistDto {
    @Getter
    @Schema(name = "ArtistCreateRequest", description = "작가 등록")
    public static class CreateRequest {
        @NotBlank @Size(max = 150)
        private String name;

        @NotBlank @Size(max = 180)
        private String slug;

        private String description;
        private String artistNote;
        private String profileImageUrl;
    }

    @Getter
    @Schema(name = "ArtistUpdateRequest", description = "작가 수정")
    public static class UpdateRequest {
        @NotBlank @Size(max = 150)
        private String name;

        @NotBlank @Size(max = 180)
        private String slug;

        private String description;
        private String artistNote;
        private String profileImageUrl;
    }

    @Getter
    @Schema(name = "ArtistMediaAddRequest", description = "작가 사진 업로드에 함께 보내는 정보. 역할이 EXHIBITION 이면 작가당 5장까지다")
    public static class MediaAddRequest {
        @NotBlank
        private String mediaType;

        @NotBlank
        private String mediaRole;

        private String title;
        private String thumbnailUrl;
        private Integer sortOrder;
    }

    @Getter
    @Schema(description = "이미 올라간 파일의 주소로 등록. 같은 역할의 기존 사진을 지우고 새로 넣는다")
    public static class MediaUrlRequest {
        @NotBlank
        private String fileUrl;

        @NotBlank
        private String mediaType;

        @NotBlank
        private String mediaRole;

        private String title;
        private String thumbnailUrl;
        private Integer sortOrder;
    }

    @Getter
    @Schema(description = "사진 썸네일 주소 변경")
    public static class MediaThumbnailRequest {
        @NotBlank
        private String thumbnailUrl;
    }

    @Getter
    @Schema(description = "약력 추가")
    public static class CareerAddRequest {
        @NotBlank
        private String category;

        @Min(1900) @Max(2100)
        private Integer year;

        @NotBlank @Size(max = 1000)
        private String content;

        private Integer sortOrder;
    }

    @Getter
    @Schema(description = "약력 수정")
    public static class CareerUpdateRequest {
        @NotBlank
        private String category;

        @Min(1900) @Max(2100)
        private Integer year;

        @NotBlank @Size(max = 1000)
        private String content;

        private Integer sortOrder;
    }

    @Getter
    @Builder
    @Schema(name = "ArtistSummaryResponse", description = "작가 목록에 쓰는 요약")
    public static class SummaryResponse {
        private Long id;
        private String artistCode;
        private String name;
        private String slug;
        private String description;
        private String profileImageUrl;
        private Boolean isActive;
        private List<MediaResponse> mediaList;
        private Long followCount;
        private FeaturedSkuInfo featuredSku;

        public static SummaryResponse from(Artist a) {
            return SummaryResponse.builder()
                    .id(a.getId())
                    .artistCode(a.getArtistCode())
                    .name(a.getName())
                    .slug(a.getSlug())
                    .description(a.getDescription())
                    .profileImageUrl(a.getProfileImageUrl())
                    .isActive(a.getIsActive())
                    .mediaList(List.of())
                    .followCount(0L)
                    .build();
        }

        public static SummaryResponse fromWithMedia(Artist a,
                                                    List<ArtistMedia> media,
                                                    long followCount,
                                                    Sku featuredSku,
                                                    VatPolicy vat, Set<String> exempt) {
            return SummaryResponse.builder()
                    .id(a.getId())
                    .artistCode(a.getArtistCode())
                    .name(a.getName())
                    .slug(a.getSlug())
                    .description(a.getDescription())
                    .profileImageUrl(a.getProfileImageUrl())
                    .isActive(a.getIsActive())
                    .mediaList(media.stream().map(MediaResponse::from).toList())
                    .followCount(followCount)
                    .featuredSku(featuredSku != null ? FeaturedSkuInfo.from(featuredSku, vat, exempt) : null)
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(description = "작가 카드에 거는 대표 작품. 가격은 부가세를 더한 값이다")
    public static class FeaturedSkuInfo {
        private String skuCode;
        private String name;
        private BigDecimal listPrice;
        private BigDecimal salePrice;
        /** 화면에 보이는 금액 — 공급가액 + 부가세 */
        private BigDecimal displayPrice;
        private BigDecimal displayListPrice;
        private String imageUrl;
        private String description;

        public static FeaturedSkuInfo from(Sku sku, VatPolicy vat, Set<String> exempt) {
            return FeaturedSkuInfo.builder()
                    .skuCode(sku.getSkuCode())
                    .name(sku.getName())
                    .listPrice(sku.getListPrice())
                    .salePrice(sku.getSalePrice())
                    .displayPrice(vat.grossOf(sku.getEffectivePrice(), sku.getMainCategory(), exempt))
                    .displayListPrice(vat.grossOf(sku.getListPrice(), sku.getMainCategory(), exempt))
                    .imageUrl(sku.getPrimaryImageUrl())
                    .description(sku.getDescription())
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(description = "대표 작품을 고를 때 보는 목록. 가격은 부가세를 더한 값이다")
    public static class ArtistSkuItem {
        private String skuCode;
        private String name;
        private BigDecimal listPrice;
        private BigDecimal salePrice;
        /** 화면에 보이는 금액 — 공급가액 + 부가세 */
        private BigDecimal displayPrice;
        private BigDecimal displayListPrice;
        private String imageUrl;
        private String status;

        public static ArtistSkuItem from(Sku sku, VatPolicy vat, Set<String> exempt) {
            return ArtistSkuItem.builder()
                    .skuCode(sku.getSkuCode())
                    .name(sku.getName())
                    .listPrice(sku.getListPrice())
                    .salePrice(sku.getSalePrice())
                    .displayPrice(vat.grossOf(sku.getEffectivePrice(), sku.getMainCategory(), exempt))
                    .displayListPrice(vat.grossOf(sku.getListPrice(), sku.getMainCategory(), exempt))
                    .imageUrl(sku.getPrimaryImageUrl())
                    .status(sku.getStatus())
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(name = "ArtistDetailResponse", description = "작가 상세")
    public static class DetailResponse {
        private Long id;
        private String artistCode;
        private String name;
        private String slug;
        private String description;
        private String artistNote;
        private String profileImageUrl;
        private Boolean isActive;
        private List<MediaResponse>  mediaList;
        private List<CareerResponse> careerList;
        private long followCount;
        private boolean isFollowing;
        // 어드민 대표작품 화면이 지금 무엇이 걸려 있는지 알아야 표시할 수 있다.
        private FeaturedSkuInfo featuredSku;

        public static DetailResponse from(Artist a, List<ArtistMedia> media,
                                          List<ArtistCareer> careers,
                                          long followCount, boolean isFollowing,
                                          Sku featuredSku,
                                          VatPolicy vat, Set<String> exempt) {
            return DetailResponse.builder()
                    .id(a.getId())
                    .artistCode(a.getArtistCode())
                    .name(a.getName())
                    .slug(a.getSlug())
                    .description(a.getDescription())
                    .artistNote(a.getArtistNote())
                    .profileImageUrl(a.getProfileImageUrl())
                    .isActive(a.getIsActive())
                    .mediaList(media.stream().map(MediaResponse::from).toList())
                    .careerList(careers.stream().map(CareerResponse::from).toList())
                    .followCount(followCount)
                    .isFollowing(isFollowing)
                    .featuredSku(featuredSku != null ? FeaturedSkuInfo.from(featuredSku, vat, exempt) : null)
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(description = "약력 한 줄")
    public static class CareerResponse {
        private Long id;
        private String category;
        private Integer year;
        private String content;
        private Integer sortOrder;

        public static CareerResponse from(ArtistCareer c) {
            return CareerResponse.builder()
                    .id(c.getId())
                    .category(c.getCategory())
                    .year(c.getYear())
                    .content(c.getContent())
                    .sortOrder(c.getSortOrder())
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(name = "ArtistMediaResponse", description = "작가 사진 한 장")
    public static class MediaResponse {
        private Long id;
        private String mediaType;
        private String mediaRole;
        private String fileUrl;
        private String thumbnailUrl;
        private String title;
        private Integer sortOrder;

        public static MediaResponse from(ArtistMedia m) {
            return MediaResponse.builder()
                    .id(m.getId())
                    .mediaType(m.getMediaType())
                    .mediaRole(m.getMediaRole())
                    .fileUrl(m.getFileUrl())
                    .thumbnailUrl(m.getThumbnailUrl())
                    .title(m.getTitle())
                    .sortOrder(m.getSortOrder())
                    .build();
        }
    }
}
