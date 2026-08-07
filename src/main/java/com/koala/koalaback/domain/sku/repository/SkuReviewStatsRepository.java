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

    /** 여러 SKU 의 리뷰 집계를 한 번에 조회 — 목록 조회 N+1 제거용 */
    List<SkuReviewStats> findAllBySkuIdIn(List<Long> skuIds);

    /**
     * 리뷰 집계 재계산 — nativeQuery 사용으로 도메인 간 순환 의존 제거.
     * SkuReview 엔티티를 JPQL로 직접 참조하면 sku ↔ review 패키지 순환 의존이 생기므로
     * 테이블명 기준 네이티브 쿼리로 처리.
     * ReviewService에서 리뷰 승인/반려 후 호출.
     */
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

