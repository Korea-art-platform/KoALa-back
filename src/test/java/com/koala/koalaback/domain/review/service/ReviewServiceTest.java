package com.koala.koalaback.domain.review.service;

import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.order.repository.OrderItemRepository;
import com.koala.koalaback.domain.review.dto.ReviewDto;
import com.koala.koalaback.domain.review.entity.SkuReview;
import com.koala.koalaback.domain.review.repository.SkuReviewRepository;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.entity.SkuReviewStats;
import com.koala.koalaback.domain.sku.repository.SkuReviewStatsRepository;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.service.UserService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.util.CodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @InjectMocks
    private ReviewService reviewService;

    @Mock private SkuReviewRepository skuReviewRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserService userService;
    @Mock private CodeGenerator codeGenerator;
    @Mock private SkuReviewStatsRepository skuReviewStatsRepository;

    @Test
    @DisplayName("리뷰 작성 성공")
    void createReview_success() {
        Long userId = 1L;
        ReviewDto.CreateRequest req = mock(ReviewDto.CreateRequest.class);
        given(req.getOrderItemId()).willReturn(1L);
        given(req.getRating()).willReturn(5);
        given(req.getTitle()).willReturn("좋아요");
        given(req.getContent()).willReturn("정말 좋은 상품입니다.");
        given(req.getMediaList()).willReturn(Collections.emptyList());

        Sku sku = mock(Sku.class);
        given(sku.getSkuCode()).willReturn("SKU-001");

        OrderItem orderItem = mock(OrderItem.class);
        given(orderItem.getSku()).willReturn(sku);

        User user = mock(User.class);

        given(skuReviewRepository.existsByOrderItemId(1L)).willReturn(false);
        given(orderItemRepository.findByIdAndOrderUserId(1L, userId))
                .willReturn(Optional.of(orderItem));
        given(userService.getUserById(userId)).willReturn(user);
        given(codeGenerator.generateReviewCode()).willReturn("REV-TESTCODE");
        given(skuReviewRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ReviewDto.ReviewResponse result = reviewService.createReview(userId, req);

        assertThat(result).isNotNull();
        assertThat(result.getReviewCode()).isEqualTo("REV-TESTCODE");
        then(orderItem).should().markReviewWritten();
    }

    @Test
    @DisplayName("리뷰 작성 실패 — 이미 작성된 리뷰")
    void createReview_fail_already_exists() {
        Long userId = 1L;
        ReviewDto.CreateRequest req = mock(ReviewDto.CreateRequest.class);
        given(req.getOrderItemId()).willReturn(1L);
        given(skuReviewRepository.existsByOrderItemId(1L)).willReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(userId, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("리뷰 삭제 실패 — 본인 리뷰 아님")
    void deleteReview_fail_not_owner() {
        Long userId = 1L;
        String reviewCode = "REV-001";

        User anotherUser = mock(User.class);
        given(anotherUser.getId()).willReturn(99L);

        SkuReview review = mock(SkuReview.class);
        given(review.getUser()).willReturn(anotherUser);

        given(skuReviewRepository.findByReviewCodeAndDeletedAtIsNull(reviewCode))
                .willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview(userId, reviewCode))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("이미 지운 리뷰는 다시 지울 수 없다 — 지울 때마다 평점이 깎이던 문제")
    void deleteReview_fail_already_deleted() {
        given(skuReviewRepository.findByReviewCodeAndDeletedAtIsNull("REV-DEL"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.deleteReview(1L, "REV-DEL"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    @DisplayName("승인된 리뷰를 고치면 검수 대기로 돌아가고 평점에서 빠진다")
    void updateReview_resets_moderation() {
        Long userId = 1L;
        String reviewCode = "REV-002";

        User user = mock(User.class);
        given(user.getId()).willReturn(userId);

        Sku sku = mock(Sku.class);
        given(sku.getId()).willReturn(7L);

        SkuReview review = mock(SkuReview.class);
        given(review.getUser()).willReturn(user);
        given(review.isApproved()).willReturn(true);
        given(review.getRating()).willReturn(5);
        given(review.getSku()).willReturn(sku);

        SkuReviewStats stats = mock(SkuReviewStats.class);
        given(skuReviewStatsRepository.findBySkuId(7L)).willReturn(Optional.of(stats));
        given(skuReviewRepository.findByReviewCodeAndDeletedAtIsNull(reviewCode))
                .willReturn(Optional.of(review));

        ReviewDto.UpdateRequest req = mock(ReviewDto.UpdateRequest.class);
        given(req.getRating()).willReturn(3);
        given(req.getTitle()).willReturn("수정한 제목");
        given(req.getContent()).willReturn("내용을 바꿨습니다.");

        reviewService.updateReview(userId, reviewCode, req);

        then(review).should().resetModeration();
        then(stats).should().removeReview(5);
    }
}
