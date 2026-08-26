package com.koala.koalaback.domain.sku.repository;

import com.koala.koalaback.domain.sku.entity.Sku;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkuRepository extends JpaRepository<Sku, Long> {
    Optional<Sku> findBySkuCode(String skuCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sku s WHERE s.id = :id")
    Optional<Sku> findByIdForUpdate(@Param("id") Long id);

    Optional<Sku> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Sku> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

    Page<Sku> findByArtistIdAndStatusAndDeletedAtIsNull(Long artistId, String status, Pageable pageable);

    @Query("SELECT s FROM Sku s WHERE s.genre = :genre AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    Page<Sku> findActiveByGenre(@Param("genre") String genre, Pageable pageable);

    @Query("SELECT s FROM Sku s WHERE s.mainCategory = :mainCategory AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    Page<Sku> findActiveByMainCategory(@Param("mainCategory") String mainCategory, Pageable pageable);

    @Query("""
        SELECT s FROM Sku s
        WHERE s.genre = :genre AND s.mainCategory = :mainCategory
          AND s.status = 'ACTIVE' AND s.deletedAt IS NULL
        """)
    Page<Sku> findActiveByGenreAndMainCategory(@Param("genre") String genre,
                                               @Param("mainCategory") String mainCategory,
                                               Pageable pageable);

    @Query("""
        SELECT s FROM Sku s
        WHERE s.status = 'ACTIVE'
          AND s.deletedAt IS NULL
          AND (s.name LIKE :keyword ESCAPE '\\' OR s.description LIKE :keyword ESCAPE '\\')
        """)
    Page<Sku> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT s FROM Sku s WHERE s.isLimitedEdition = true AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    Page<Sku> findLimitedEditions(Pageable pageable);

    @Query("SELECT s.genre, COUNT(s) FROM Sku s WHERE s.status = 'ACTIVE' AND s.deletedAt IS NULL GROUP BY s.genre")
    List<Object[]> countByGenre();

    @Query("SELECT s.mainCategory, COUNT(s) FROM Sku s WHERE s.status = 'ACTIVE' AND s.deletedAt IS NULL GROUP BY s.mainCategory")
    List<Object[]> countByMainCategory();

    long countByMainCategoryAndDeletedAtIsNull(String mainCategory);

    long countByGenreAndDeletedAtIsNull(String genre);

    long countByStatusAndDeletedAtIsNull(String status);

    Page<Sku> findByDeletedAtIsNull(Pageable pageable);

    @Query("SELECT s FROM Sku s WHERE s.skuCode IN :skuCodes")
    List<Sku> findAllBySkuCodeIn(@Param("skuCodes") List<String> skuCodes);

    @Query("SELECT s.slug FROM Sku s WHERE s.slug IN :slugs")
    List<String> findExistingSlugs(@Param("slugs") List<String> slugs);

    List<Sku> findByArtistIdAndDeletedAtIsNull(Long artistId);
}
