package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.review.dto.ReviewDto;
import com.koala.koalaback.domain.review.service.ReviewService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/v1/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {
    private final ReviewService reviewService;

    @GetMapping("/pending")
    public ApiResponse<PageResponse<ReviewDto.ReviewResponse>> getPendingReviews(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(reviewService.getPendingReviews(pageable));
    }

    @PatchMapping("/{reviewCode}/moderate")
    public ApiResponse<ReviewDto.ReviewResponse> moderateReview(
            @PathVariable String reviewCode,
            @Valid @RequestBody ReviewDto.ModerateRequest req) {
        return ApiResponse.ok(reviewService.moderateReview(reviewCode, req));
    }

    @PatchMapping("/{reviewCode}/featured")
    public ApiResponse<Void> setFeatured(
            @PathVariable String reviewCode,
            @RequestParam boolean featured) {
        reviewService.setFeatured(reviewCode, featured);
        return ApiResponse.ok();
    }
}
