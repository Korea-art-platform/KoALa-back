package com.koala.koalaback.domain.banner.dto;

import com.koala.koalaback.domain.banner.entity.Banner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class BannerDto {
    @Getter
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

        @Size(max = 700)
        private String linkUrl;

        private String linkTarget;
        private String bgColor;
        private String textColor;
        private Integer sortOrder;
        private LocalDateTime visibleFrom;
        private LocalDateTime visibleTo;
    }

    @Getter
    public static class UpdateRequest {
        @NotBlank @Size(max = 200)
        private String title;

        @Size(max = 255)
        private String subtitle;

        @Size(max = 100)
        private String badge;

        @Size(max = 500)
        private String description;

        @NotBlank @Size(max = 700)
        private String imageUrl;

        @Size(max = 700)
        private String mobileImageUrl;

        @Size(max = 700)
        private String linkUrl;

        private String linkTarget;
        private String bgColor;
        private String textColor;
        private Integer sortOrder;
        private LocalDateTime visibleFrom;
        private LocalDateTime visibleTo;
    }

    @Getter
    @Builder
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
        private String linkUrl;
        private String linkTarget;
        private String bgColor;
        private String textColor;
        private Integer sortOrder;
        private Boolean isActive;
        private LocalDateTime visibleFrom;
        private LocalDateTime visibleTo;
        private LocalDateTime createdAt;

        public static BannerResponse from(Banner b) {
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
