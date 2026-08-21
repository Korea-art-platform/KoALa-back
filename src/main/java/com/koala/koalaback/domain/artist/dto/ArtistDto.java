package com.koala.koalaback.domain.artist.dto;

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

import java.math.BigDecimal;
import java.util.List;

public class ArtistDto {
    @Getter
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
    public static class MediaThumbnailRequest {
        @NotBlank
        private String thumbnailUrl;
    }

    @Getter
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
                                                    Sku featuredSku) {
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
                    .featuredSku(featuredSku != null ? FeaturedSkuInfo.from(featuredSku) : null)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class FeaturedSkuInfo {
        private String skuCode;
        private String name;
        private BigDecimal listPrice;
        private BigDecimal salePrice;
        private String imageUrl;
        private String description;

        public static FeaturedSkuInfo from(Sku sku) {
            return FeaturedSkuInfo.builder()
                    .skuCode(sku.getSkuCode())
                    .name(sku.getName())
                    .listPrice(sku.getListPrice())
                    .salePrice(sku.getSalePrice())
                    .imageUrl(sku.getPrimaryImageUrl())
                    .description(sku.getDescription())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ArtistSkuItem {
        private String skuCode;
        private String name;
        private BigDecimal listPrice;
        private BigDecimal salePrice;
        private String imageUrl;
        private String status;

        public static ArtistSkuItem from(Sku sku) {
            return ArtistSkuItem.builder()
                    .skuCode(sku.getSkuCode())
                    .name(sku.getName())
                    .listPrice(sku.getListPrice())
                    .salePrice(sku.getSalePrice())
                    .imageUrl(sku.getPrimaryImageUrl())
                    .status(sku.getStatus())
                    .build();
        }
    }

    @Getter
    @Builder
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

        public static DetailResponse from(Artist a, List<ArtistMedia> media,
                                          List<ArtistCareer> careers,
                                          long followCount, boolean isFollowing) {
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
                    .build();
        }
    }

    @Getter
    @Builder
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
