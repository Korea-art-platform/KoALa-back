package com.koala.koalaback.api.review;

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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "리뷰", description = "산 사람만 쓴다. 관리자 승인을 거쳐야 상품 화면에 걸린다")
@RestController
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @Operation(summary = "리뷰 작성", description = """
            내 주문의 항목(orderItemId)으로만 쓸 수 있다. 주문 항목과 userId 를 함께 걸어
            찾으므로 사지 않은 작품에는 쓸 수 없다.

            한 주문 항목에 하나만 쓴다. 같은 것을 두 번 사면 두 번 쓸 수 있다.

            쓰고 나면 작성 완료로 표시해 주문 화면에서 다시 권하지 않는다.

            바로 상품 화면에 걸리지 않는다 — 승인을 거친 것만 보인다.
            """)
    @PostMapping("/api/v1/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewDto.ReviewResponse> createReview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReviewDto.CreateRequest req) {
        return ApiResponse.ok(reviewService.createReview(userId, req));
    }

    @Operation(summary = "리뷰 수정", description = "쓴 사람만 고칠 수 있다. 아니면 FORBIDDEN.")
    @PatchMapping("/api/v1/reviews/{reviewCode}")
    public ApiResponse<ReviewDto.ReviewResponse> updateReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reviewCode,
            @Valid @RequestBody ReviewDto.UpdateRequest req) {
        return ApiResponse.ok(reviewService.updateReview(userId, reviewCode, req));
    }

    @Operation(summary = "리뷰 삭제", description = """
            소프트 삭제다. 승인된 리뷰였다면 그 상품의 평점 집계에서 이 별점을 빼고
            지운다 — 빼지 않으면 사라진 리뷰가 평균에 계속 남는다.
            """)
    @DeleteMapping("/api/v1/reviews/{reviewCode}")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reviewCode) {
        reviewService.deleteReview(userId, reviewCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "상품 리뷰 목록", description = """
            승인된 것만, 삭제되지 않은 것만 내려간다. 기본 20건씩.
            """)
    @GetMapping("/api/v1/skus/{skuCode}/reviews")
    public ApiResponse<PageResponse<ReviewDto.ReviewResponse>> getSkuReviews(
            @PathVariable String skuCode,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(reviewService.getSkuReviews(skuCode, pageable));
    }

    @Operation(summary = "내가 쓴 리뷰", description = """
            아직 승인되지 않은 것도 보인다. 상품 화면과 달리 본인 것이라 감출 이유가 없다.
            """)
    @GetMapping("/api/v1/reviews/me")
    public ApiResponse<PageResponse<ReviewDto.ReviewResponse>> getMyReviews(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(reviewService.getMyReviews(userId, pageable));
    }
}
