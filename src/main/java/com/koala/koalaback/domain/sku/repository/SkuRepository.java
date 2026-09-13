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

    /**
     * 스토어 목록 — 들어온 조건만 건다(null 이면 안 건다).
     *
     * 가격은 화면에 보이는 금액으로 비교한다. 면세 대분류는 공급가액 그대로,
     * 나머지는 부가세 10% 를 더한다. 공급가액으로 비교하면 "50만 원 이하"에
     * 55만 원짜리가 섞여 나온다. 정렬도 같은 금액을 쓴다.
     * 추천순은 원작 대분류(originals)를 먼저 걸고, 그 안에서 최근 공개 순이다.
     * 원작을 면세 표시로 가르지 않는다 — 원작도 과세로 바뀔 수 있다.
     *
     * exempt·originals 가 비면 IN () 이 깨지므로 호출하는 쪽에서 빈 값을 채워 넘긴다.
     */
    @Query(value = """
        SELECT s FROM Sku s
        WHERE s.status = 'ACTIVE' AND s.deletedAt IS NULL
          AND (:genre IS NULL OR s.genre = :genre)
          AND (:mainCategory IS NULL OR s.mainCategory = :mainCategory)
          AND (:artistCode IS NULL OR s.artist.artistCode = :artistCode)
          AND (:minPrice IS NULL OR
               (CASE WHEN s.mainCategory IN :exempt THEN COALESCE(s.salePrice, s.listPrice)
                     ELSE COALESCE(s.salePrice, s.listPrice) * 1.1 END) >= :minPrice)
          AND (:maxPrice IS NULL OR
               (CASE WHEN s.mainCategory IN :exempt THEN COALESCE(s.salePrice, s.listPrice)
                     ELSE COALESCE(s.salePrice, s.listPrice) * 1.1 END) <= :maxPrice)
        ORDER BY
          CASE WHEN :order = 'PRICE_ASC' THEN
               (CASE WHEN s.mainCategory IN :exempt THEN COALESCE(s.salePrice, s.listPrice)
                     ELSE COALESCE(s.salePrice, s.listPrice) * 1.1 END) END ASC,
          CASE WHEN :order = 'PRICE_DESC' THEN
               (CASE WHEN s.mainCategory IN :exempt THEN COALESCE(s.salePrice, s.listPrice)
                     ELSE COALESCE(s.salePrice, s.listPrice) * 1.1 END) END DESC,
          CASE WHEN :order = 'RECOMMENDED' AND s.mainCategory IN :originals THEN 0 ELSE 1 END ASC,
          s.publishedAt DESC, s.id DESC
        """,
        countQuery = """
        SELECT COUNT(s) FROM Sku s
        WHERE s.status = 'ACTIVE' AND s.deletedAt IS NULL
          AND (:genre IS NULL OR s.genre = :genre)
          AND (:mainCategory IS NULL OR s.mainCategory = :mainCategory)
          AND (:artistCode IS NULL OR s.artist.artistCode = :artistCode)
          AND (:minPrice IS NULL OR
               (CASE WHEN s.mainCategory IN :exempt THEN COALESCE(s.salePrice, s.listPrice)
                     ELSE COALESCE(s.salePrice, s.listPrice) * 1.1 END) >= :minPrice)
          AND (:maxPrice IS NULL OR
               (CASE WHEN s.mainCategory IN :exempt THEN COALESCE(s.salePrice, s.listPrice)
                     ELSE COALESCE(s.salePrice, s.listPrice) * 1.1 END) <= :maxPrice)
        """)
    Page<Sku> findStoreList(@Param("genre") String genre,
                            @Param("mainCategory") String mainCategory,
                            @Param("artistCode") String artistCode,
                            @Param("minPrice") java.math.BigDecimal minPrice,
                            @Param("maxPrice") java.math.BigDecimal maxPrice,
                            @Param("exempt") java.util.Collection<String> exempt,
                            @Param("originals") java.util.Collection<String> originals,
                            @Param("order") String order,
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
