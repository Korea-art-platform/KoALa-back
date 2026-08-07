package com.koala.koalaback.domain.sku.repository;

import com.koala.koalaback.domain.sku.entity.SkuStockLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkuStockLedgerRepository extends JpaRepository<SkuStockLedger, Long> {

    /** 현재 재고 합계 — delta 원장 방식 핵심 쿼리 */
    @Query("SELECT COALESCE(SUM(l.delta), 0) FROM SkuStockLedger l WHERE l.sku.id = :skuId")
    int sumDeltaBySkuId(@Param("skuId") Long skuId);

    /**
     * 여러 SKU 의 재고 합계를 한 번에 집계 — 목록 조회 N+1 제거용.
     *
     * <p>원장에 행이 하나도 없는 SKU 는 GROUP BY 결과에서 빠지므로,
     * 호출측에서 누락분을 0 으로 채워야 한다.
     */
    @Query("""
        SELECT l.sku.id AS skuId, COALESCE(SUM(l.delta), 0) AS totalDelta
        FROM SkuStockLedger l
        WHERE l.sku.id IN :skuIds
        GROUP BY l.sku.id
        """)
    List<StockSum> sumDeltaBySkuIdIn(@Param("skuIds") List<Long> skuIds);

    /** {@link #sumDeltaBySkuIdIn} 결과 projection */
    interface StockSum {
        Long getSkuId();
        Long getTotalDelta();
    }

    /** 재고 변동 이력 조회 — 어드민 재고 히스토리 화면용 */
    Page<SkuStockLedger> findBySkuIdOrderByCreatedAtDesc(Long skuId, Pageable pageable);

    /** 특정 참조(주문 아이템 등) 연결된 원장 조회 */
    List<SkuStockLedger> findByRefTypeAndRefId(String refType, Long refId);
}

