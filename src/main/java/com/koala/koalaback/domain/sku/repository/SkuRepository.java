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

    /**
     * 재고 차감 시 사용하는 비관적 쓰기 락.
     * 동시 주문으로 인한 재고 Race Condition 방지 — 트랜잭션 종료까지 row 잠금.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sku s WHERE s.id = :id")
    Optional<Sku> findByIdForUpdate(@Param("id") Long id);

    Optional<Sku> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Sku> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

    Page<Sku> findByArtistIdAndStatusAndDeletedAtIsNull(Long artistId, String status, Pageable pageable);

    /** 장르 필터 */
    @Query("SELECT s FROM Sku s WHERE s.genre = :genre AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    Page<Sku> findActiveByGenre(@Param("genre") String genre, Pageable pageable);

    /**
     * 키워드 검색 (name, description).
     * ESCAPE '\' 를 사용해 LIKE 와일드카드(%, _)를 안전하게 처리합니다.
     * 호출부에서 반드시 {@link com.koala.koalaback.global.util.LikeEscapeUtil#escape(String)} 로
     * 파라미터를 이스케이프한 뒤 %keyword% 형태로 전달하세요.
     */
    @Query("""
        SELECT s FROM Sku s
        WHERE s.status = 'ACTIVE'
          AND s.deletedAt IS NULL
          AND (s.name LIKE :keyword ESCAPE '\\' OR s.description LIKE :keyword ESCAPE '\\')
        """)
    Page<Sku> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /** 한정판 목록 */
    @Query("SELECT s FROM Sku s WHERE s.isLimitedEdition = true AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    Page<Sku> findLimitedEditions(Pageable pageable);

    /** 장르별 상품 수 (ACTIVE, 미삭제) */
    @Query("SELECT s.genre, COUNT(s) FROM Sku s WHERE s.status = 'ACTIVE' AND s.deletedAt IS NULL GROUP BY s.genre")
    List<Object[]> countByGenre();

    /** 전체 ACTIVE 상품 수 */
    long countByStatusAndDeletedAtIsNull(String status);

    /** 어드민 전체 조회 (삭제 포함 옵션) */
    Page<Sku> findByDeletedAtIsNull(Pageable pageable);

    /** skuCode 목록으로 일괄 조회 — 장바구니/주문 검증용 */
    @Query("SELECT s FROM Sku s WHERE s.skuCode IN :skuCodes")
    List<Sku> findAllBySkuCodeIn(@Param("skuCodes") List<String> skuCodes);

    /** csv 일괄 등록 slug 중복 검사 — 이미 있는 slug 만 반환 */
    @Query("SELECT s.slug FROM Sku s WHERE s.slug IN :slugs")
    List<String> findExistingSlugs(@Param("slugs") List<String> slugs);

    /** 작가 삭제 시 연관 상품 일괄 soft-delete용 */
    List<Sku> findByArtistIdAndDeletedAtIsNull(Long artistId);
}

