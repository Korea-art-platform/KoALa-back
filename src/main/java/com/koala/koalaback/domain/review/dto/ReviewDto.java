package com.koala.koalaback.domain.review.dto;

import com.koala.koalaback.domain.review.entity.SkuReview;
import com.koala.koalaback.domain.review.entity.SkuReviewMedia;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public class ReviewDto {
    @Getter
    public static class CreateRequest {
        @NotNull
        private Long orderItemId;

        @NotNull @Min(1) @Max(5)
        private Integer rating;

        @Size(max = 200)
        private String title;

        @NotBlank @Size(max = 2000)
        private String content;

        private List<MediaItem> mediaList;
    }

    @Getter
    public static class UpdateRequest {
        @NotNull @Min(1) @Max(5)
        private Integer rating;

        @Size(max = 200)
        private String title;

        @NotBlank @Size(max = 2000)
        private String content;
    }

    @Getter
    public static class MediaItem {
        @NotBlank
        private String mediaType;

        @NotBlank
        private String fileUrl;

        private String thumbnailUrl;
        private Integer sortOrder;
    }

    @Getter
    public static class ModerateRequest {
        @NotBlank
        private String action;

        private String memo;
    }

    @Getter
    @Builder
    public static class ReviewResponse {
        private Long id;
        private String reviewCode;
        private String skuCode;
        private String skuName;
        private String userCode;
        private String userName;
        private Integer rating;
        private String title;
        private String content;
        private String reviewStatus;
        private Boolean isVisible;
        private Boolean isFeatured;
        private Integer likeCount;
        private Integer reportCount;
        private List<ReviewMediaResponse> mediaList;
        private LocalDateTime createdAt;

        public static ReviewResponse from(SkuReview r) {
            return ReviewResponse.builder()
                    .id(r.getId())
                    .reviewCode(r.getReviewCode())
                    .skuCode(r.getSku().getSkuCode())
                    .skuName(r.getSku().getName())
                    .userCode(r.getUser().getUserCode())
                    .userName(r.getUser().getName())
                    .rating(r.getRating())
                    .title(r.getTitle())
                    .content(r.getContent())
                    .reviewStatus(r.getReviewStatus())
                    .isVisible(r.getIsVisible())
                    .isFeatured(r.getIsFeatured())
                    .likeCount(r.getLikeCount())
                    .reportCount(r.getReportCount())
                    .mediaList(r.getMediaList().stream()
                            .map(ReviewMediaResponse::from).toList())
                    .createdAt(r.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ReviewMediaResponse {
        private Long id;
        private String mediaType;
        private String fileUrl;
        private String thumbnailUrl;
        private Integer sortOrder;

        public static ReviewMediaResponse from(SkuReviewMedia m) {
            return ReviewMediaResponse.builder()
                    .id(m.getId())
                    .mediaType(m.getMediaType())
                    .fileUrl(m.getFileUrl())
                    .thumbnailUrl(m.getThumbnailUrl())
                    .sortOrder(m.getSortOrder())
                    .build();
        }
    }
}
