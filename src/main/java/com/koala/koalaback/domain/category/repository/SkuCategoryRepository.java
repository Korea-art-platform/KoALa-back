package com.koala.koalaback.domain.category.repository;

import com.koala.koalaback.domain.category.entity.SkuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkuCategoryRepository extends JpaRepository<SkuCategory, Long> {

    /** 어드민 목록 — 비활성 포함 */
    List<SkuCategory> findAllByOrderByTypeAscSortOrderAsc();

    /** 공개 목록 — 활성만 */
    List<SkuCategory> findByIsActiveTrueOrderByTypeAscSortOrderAsc();

    boolean existsByTypeAndCode(String type, String code);

    Optional<SkuCategory> findByTypeAndCode(String type, String code);

    /** 새 카테고리의 기본 정렬값 계산용 */
    @Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM SkuCategory c WHERE c.type = :type")
    int findMaxSortOrder(@Param("type") String type);

    /**
     * 카테고리별 사용 중인 상품 수 — 비활성화 전에 경고를 띄우기 위해.
     *
     * <p>Sku 를 JPQL 로 직접 참조하면 category ↔ sku 패키지 순환 의존이 생기므로
     * 테이블명 기준 네이티브 쿼리로 처리한다.
     */
    @Query(value = """
        SELECT c.id AS categoryId,
               (SELECT COUNT(*) FROM skus s
                 WHERE s.deleted_at IS NULL
                   AND ((c.type = 'MAIN' AND s.main_category = c.code)
                     OR (c.type = 'SUB'  AND s.genre = c.code))) AS usedCount
          FROM sku_categories c
        """, nativeQuery = true)
    List<CategoryUsage> findUsageCounts();

    /** {@link #findUsageCounts()} 결과 projection */
    interface CategoryUsage {
        Long getCategoryId();
        Long getUsedCount();
    }
}
