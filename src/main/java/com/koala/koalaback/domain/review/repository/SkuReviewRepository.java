package com.koala.koalaback.domain.review.repository;

import com.koala.koalaback.domain.review.entity.SkuReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SkuReviewRepository extends JpaRepository<SkuReview, Long> {
    boolean existsByOrderItemId(Long orderItemId);

    Optional<SkuReview> findByReviewCode(String reviewCode);

    Page<SkuReview> findBySkuIdAndReviewStatusAndDeletedAtIsNull(
            Long skuId, String reviewStatus, Pageable pageable);

    Page<SkuReview> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    Page<SkuReview> findByReviewStatusAndDeletedAtIsNull(String reviewStatus, Pageable pageable);

    @Query("SELECT r.sku.id FROM SkuReview r WHERE r.sku.skuCode = :skuCode")
    Optional<Long> findSkuIdBySkuCode(@Param("skuCode") String skuCode);
}
