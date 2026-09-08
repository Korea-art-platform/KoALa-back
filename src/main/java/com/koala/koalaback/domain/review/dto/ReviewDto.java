package com.koala.koalaback.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.review.entity.SkuReview;
import com.koala.koalaback.domain.review.entity.SkuReviewMedia;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public class ReviewDto {
    @Getter
    @Schema(name = "ReviewCreateRequest",
            description = "리뷰 작성. 내가 산 주문 항목으로만 쓸 수 있고 한 항목에 하나만 쓴다")
    public static class CreateRequest {
        @NotNull
        @Schema(description = "주문 항목 id. 내 주문의 것이어야 한다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Long orderItemId;

        @NotNull @Min(1) @Max(5)
        @Schema(description = "별점. 1~5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer rating;

        @Size(max = 200)
        @Schema(description = "제목. 200자까지")
        private String title;

        @NotBlank @Size(max = 2000)
        @Schema(description = "내용. 2000자까지", requiredMode = Schema.RequiredMode.REQUIRED)
        private String content;

        @Schema(description = "사진·영상. 이미 올라간 파일의 주소를 넘긴다")
        private List<MediaItem> mediaList;
    }

    @Getter
    @Schema(name = "ReviewUpdateRequest", description = "리뷰 수정. 쓴 사람만 고칠 수 있다")
    public static class UpdateRequest {
        @NotNull @Min(1) @Max(5)
        @Schema(description = "별점. 1~5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer rating;

        @Size(max = 200)
        private String title;

        @NotBlank @Size(max = 2000)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
    @Schema(description = "리뷰 승인·숨김·거절. 평점 집계도 함께 손본다")
    public static class ModerateRequest {
        @NotBlank
        @Schema(description = "APPROVE(승인) · HIDE(숨김) · REJECT(거절). 그 밖의 값은 INVALID_INPUT",
                example = "APPROVE", requiredMode = Schema.RequiredMode.REQUIRED)
        private String action;

        @Schema(description = "숨김·거절 사유")
        private String memo;
    }

    @Getter
    @Builder
    @Schema(description = "리뷰. 상품 화면에는 승인된 것만 내려간다")
    public static class ReviewResponse {
        private Long id;
        private String reviewCode;

        @Schema(example = "A1B2C3D4E5F60718")
        private String skuCode;

        private String skuName;
        private String userCode;
        private String userName;

        @Schema(description = "별점. 1~5", example = "5")
        private Integer rating;

        private String title;
        private String content;

        @Schema(description = "상태. PENDING(승인 대기) · APPROVED(승인) · HIDDEN(숨김) · REJECTED(거절)", example = "APPROVED")
        private String reviewStatus;

        @Schema(description = "화면에 보이는지", example = "true")
        private Boolean isVisible;

        @Schema(description = "대표 리뷰인지", example = "false")
        private Boolean isFeatured;

        @Schema(example = "0")
        private Integer likeCount;

        @Schema(example = "0")
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
