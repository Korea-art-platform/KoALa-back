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

    Optional<SkuCategory> findByTypeAndCode(String type, String code);

    @Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM SkuCategory c WHERE c.type = :type")
    int findMaxSortOrder(@Param("type") String type);

    @Query(value = """
        SELECT c.id AS categoryId,
               (SELECT COUNT(*) FROM skus s
                 WHERE s.deleted_at IS NULL
                   AND ((c.type = 'MAIN' AND s.main_category = c.code)
                     OR (c.type = 'SUB'  AND s.genre = c.code))) AS usedCount
          FROM sku_categories c
        """, nativeQuery = true)
    List<CategoryUsage> findUsageCounts();

    interface CategoryUsage {
        Long getCategoryId();
        Long getUsedCount();
    }
}
