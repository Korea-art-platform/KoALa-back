package com.koala.koalaback.domain.sku.repository;

import com.koala.koalaback.domain.sku.entity.SkuReviewStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkuReviewStatsRepository extends JpaRepository<SkuReviewStats, Long> {
    Optional<SkuReviewStats> findBySkuId(Long skuId);

    List<SkuReviewStats> findAllBySkuIdIn(List<Long> skuIds);

    @Modifying
    @Query(value = """
        UPDATE sku_review_stats s
        SET s.review_count = (
            SELECT COUNT(*) FROM sku_reviews r
            WHERE r.sku_id = s.sku_id
              AND r.review_status = 'APPROVED'
              AND r.deleted_at IS NULL
        ),
        s.rating_sum = (
            SELECT COALESCE(SUM(r.rating), 0) FROM sku_reviews r
            WHERE r.sku_id = s.sku_id
              AND r.review_status = 'APPROVED'
              AND r.deleted_at IS NULL
        ),
        s.avg_rating = (
            SELECT COALESCE(AVG(r.rating), 0) FROM sku_reviews r
            WHERE r.sku_id = s.sku_id
              AND r.review_status = 'APPROVED'
              AND r.deleted_at IS NULL
        ),
        s.updated_at = NOW()
        WHERE s.sku_id = :skuId
        """, nativeQuery = true)
    void recalculateBySkuId(@Param("skuId") Long skuId);
}
