package com.koala.koalaback.domain.category.repository;

import com.koala.koalaback.domain.category.entity.SkuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkuCategoryRepository extends JpaRepository<SkuCategory, Long> {
    List<SkuCategory> findAllByOrderByTypeAscSortOrderAsc();

    List<SkuCategory> findByIsActiveTrueOrderByTypeAscSortOrderAsc();

    boolean existsByTypeAndCode(String type, String code);

    boolean existsByTypeAndName(String type, String name);

    Optional<SkuCategory> findByTypeAndName(String type, String name);

    Optional<SkuCategory> findByTypeAndCode(String type, String code);

    @Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM SkuCategory c WHERE c.type = :type")
    int findMaxSortOrder(@Param("type") String type);

    /**
     * 분류별 사용 상품 수.
     *
     * sku_categories 는 utf8mb4_0900_ai_ci, skus 는 utf8mb4_unicode_ci 라
     * 그냥 비교하면 MySQL 이 "Illegal mix of collations"(1267) 로 거부한다.
     * 양쪽에 collation 을 명시해 맞춘다.
     */
    @Query(value = """
        SELECT c.id AS categoryId,
               (SELECT COUNT(*) FROM skus s
                 WHERE s.deleted_at IS NULL
                   AND ((c.type = 'MAIN'
                         AND s.main_category COLLATE utf8mb4_general_ci
                           = c.code COLLATE utf8mb4_general_ci)
                     OR (c.type = 'SUB'
                         AND s.genre COLLATE utf8mb4_general_ci
                           = c.code COLLATE utf8mb4_general_ci))) AS usedCount
          FROM sku_categories c
        """, nativeQuery = true)
    List<CategoryUsage> findUsageCounts();

    interface CategoryUsage {
        Long getCategoryId();
        Long getUsedCount();
    }
}
