package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.review.dto.ReviewDto;
import com.koala.koalaback.domain.review.service.ReviewService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "어드민 · 리뷰", description = "리뷰 승인·숨김과 대표 리뷰")
@RestController
@RequestMapping("/admin/api/v1/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {
    private final ReviewService reviewService;

    @Operation(summary = "승인 대기 리뷰", description = "아직 상품 화면에 걸리지 않은 리뷰다.")
    @GetMapping("/pending")
    public ApiResponse<PageResponse<ReviewDto.ReviewResponse>> getPendingReviews(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(reviewService.getPendingReviews(pageable));
    }

    @Operation(summary = "리뷰 승인·숨김·거절", description = """
            action 은 APPROVE · HIDE · REJECT 다. 그 밖의 값은 INVALID_INPUT.

            평점 집계를 함께 손본다. 승인되지 않았던 것을 승인하면 별점을 더하고,
            승인됐던 것을 숨기거나 거절하면 그 상품의 평점을 다시 계산한다. 이미 승인된
            것을 또 승인해도 두 번 더해지지 않는다.
            """)
    @PatchMapping("/{reviewCode}/moderate")
    public ApiResponse<ReviewDto.ReviewResponse> moderateReview(
            @PathVariable String reviewCode,
            @Valid @RequestBody ReviewDto.ModerateRequest req) {
        return ApiResponse.ok(reviewService.moderateReview(reviewCode, req));
    }

    @Operation(summary = "대표 리뷰 지정·해제")
    @PatchMapping("/{reviewCode}/featured")
    public ApiResponse<Void> setFeatured(
            @PathVariable String reviewCode,
            @RequestParam boolean featured) {
        reviewService.setFeatured(reviewCode, featured);
        return ApiResponse.ok();
    }
}
