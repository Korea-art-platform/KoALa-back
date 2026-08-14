package com.koala.koalaback.api.review;

import com.koala.koalaback.domain.review.dto.ReviewDto;
import com.koala.koalaback.domain.review.service.ReviewService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping("/api/v1/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewDto.ReviewResponse> createReview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReviewDto.CreateRequest req) {
        return ApiResponse.ok(reviewService.createReview(userId, req));
    }

    @PatchMapping("/api/v1/reviews/{reviewCode}")
    public ApiResponse<ReviewDto.ReviewResponse> updateReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reviewCode,
            @Valid @RequestBody ReviewDto.UpdateRequest req) {
        return ApiResponse.ok(reviewService.updateReview(userId, reviewCode, req));
    }

    @DeleteMapping("/api/v1/reviews/{reviewCode}")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reviewCode) {
        reviewService.deleteReview(userId, reviewCode);
        return ApiResponse.ok();
    }

    @GetMapping("/api/v1/skus/{skuCode}/reviews")
    public ApiResponse<PageResponse<ReviewDto.ReviewResponse>> getSkuReviews(
            @PathVariable String skuCode,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(reviewService.getSkuReviews(skuCode, pageable));
    }

    @GetMapping("/api/v1/reviews/me")
    public ApiResponse<PageResponse<ReviewDto.ReviewResponse>> getMyReviews(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(reviewService.getMyReviews(userId, pageable));
    }
}
