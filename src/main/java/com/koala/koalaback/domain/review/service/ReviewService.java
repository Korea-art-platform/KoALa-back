package com.koala.koalaback.domain.review.service;

import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.order.repository.OrderItemRepository;
import com.koala.koalaback.domain.review.dto.ReviewDto;
import com.koala.koalaback.domain.review.entity.SkuReview;
import com.koala.koalaback.domain.review.entity.SkuReviewMedia;
import com.koala.koalaback.domain.review.repository.SkuReviewMediaRepository;
import com.koala.koalaback.domain.review.repository.SkuReviewRepository;
import com.koala.koalaback.domain.sku.repository.SkuReviewStatsRepository;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.service.UserService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.global.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {
    private final SkuReviewRepository skuReviewRepository;
    private final SkuReviewMediaRepository skuReviewMediaRepository;
    private final SkuReviewStatsRepository skuReviewStatsRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserService userService;
    private final CodeGenerator codeGenerator;

    @Transactional
    public ReviewDto.ReviewResponse createReview(Long userId, ReviewDto.CreateRequest req) {
        if (skuReviewRepository.existsByOrderItemId(req.getOrderItemId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        OrderItem orderItem = orderItemRepository
                .findByIdAndOrderUserId(req.getOrderItemId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED));

        User user = userService.getUserById(userId);

        SkuReview review = SkuReview.builder()
                .reviewCode(codeGenerator.generateReviewCode())
                .orderItem(orderItem)
                .order(orderItem.getOrder())
                .sku(orderItem.getSku())
                .user(user)
                .rating(req.getRating())
                .title(req.getTitle())
                .content(req.getContent())
                .build();
        skuReviewRepository.save(review);

        if (req.getMediaList() != null && !req.getMediaList().isEmpty()) {
            List<SkuReviewMedia> mediaList = req.getMediaList().stream()
                    .map(m -> SkuReviewMedia.builder()
                            .review(review)
                            .mediaType(m.getMediaType())
                            .fileUrl(m.getFileUrl())
                            .thumbnailUrl(m.getThumbnailUrl())
                            .sortOrder(m.getSortOrder() != null ? m.getSortOrder() : 0)
                            .build())
                    .toList();
            skuReviewMediaRepository.saveAll(mediaList);
        }

        orderItem.markReviewWritten();

        log.info("Review created: reviewCode={}, userId={}", review.getReviewCode(), userId);
        return ReviewDto.ReviewResponse.from(review);
    }

    @Transactional
    public ReviewDto.ReviewResponse updateReview(Long userId, String reviewCode,
                                                 ReviewDto.UpdateRequest req) {
        SkuReview review = getReviewByCode(reviewCode);

        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        review.updateContent(req.getRating(), req.getTitle(), req.getContent());
        return ReviewDto.ReviewResponse.from(review);
    }

    @Transactional
    public void deleteReview(Long userId, String reviewCode) {
        SkuReview review = getReviewByCode(reviewCode);

        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (review.isApproved()) {
            skuReviewStatsRepository.findBySkuId(review.getSku().getId())
                    .ifPresent(stats -> stats.removeReview(review.getRating()));
        }

        review.softDelete();
    }

    public PageResponse<ReviewDto.ReviewResponse> getSkuReviews(String skuCode, Pageable pageable) {
        Long skuId = skuReviewRepository.findSkuIdBySkuCode(skuCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
        return PageResponse.of(
                skuReviewRepository
                        .findBySkuIdAndReviewStatusAndDeletedAtIsNull(skuId, "APPROVED", pageable)
                        .map(ReviewDto.ReviewResponse::from)
        );
    }

    public PageResponse<ReviewDto.ReviewResponse> getMyReviews(Long userId, Pageable pageable) {
        return PageResponse.of(
                skuReviewRepository.findByUserIdAndDeletedAtIsNull(userId, pageable)
                        .map(ReviewDto.ReviewResponse::from)
        );
    }

    public PageResponse<ReviewDto.ReviewResponse> getPendingReviews(Pageable pageable) {
        return PageResponse.of(
                skuReviewRepository.findByReviewStatusAndDeletedAtIsNull("PENDING", pageable)
                        .map(ReviewDto.ReviewResponse::from)
        );
    }

    @Transactional
    public ReviewDto.ReviewResponse moderateReview(String reviewCode,
                                                   ReviewDto.ModerateRequest req) {
        SkuReview review = getReviewByCode(reviewCode);
        boolean wasApproved = review.isApproved();

        switch (req.getAction()) {
            case "APPROVE" -> {
                review.approve(null);
                if (!wasApproved) {
                    skuReviewStatsRepository.findBySkuId(review.getSku().getId())
                            .ifPresent(stats -> stats.addReview(review.getRating()));
                }
            }
            case "HIDE" -> {
                review.hide(null, req.getMemo());
                if (wasApproved) {
                    skuReviewStatsRepository.recalculateBySkuId(review.getSku().getId());
                }
            }
            case "REJECT" -> {
                review.reject(null, req.getMemo());
                if (wasApproved) {
                    skuReviewStatsRepository.recalculateBySkuId(review.getSku().getId());
                }
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        return ReviewDto.ReviewResponse.from(review);
    }

    @Transactional
    public void setFeatured(String reviewCode, boolean featured) {
        getReviewByCode(reviewCode).setFeatured(featured);
    }

    private SkuReview getReviewByCode(String reviewCode) {
        return skuReviewRepository.findByReviewCode(reviewCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }
}
