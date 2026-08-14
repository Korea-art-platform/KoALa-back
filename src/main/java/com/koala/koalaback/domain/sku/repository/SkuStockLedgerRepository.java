package com.koala.koalaback.domain.sku.repository;

import com.koala.koalaback.domain.sku.entity.SkuStockLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkuStockLedgerRepository extends JpaRepository<SkuStockLedger, Long> {
    @Query("SELECT COALESCE(SUM(l.delta), 0) FROM SkuStockLedger l WHERE l.sku.id = :skuId")
    int sumDeltaBySkuId(@Param("skuId") Long skuId);

    @Query("""
        SELECT l.sku.id AS skuId, COALESCE(SUM(l.delta), 0) AS totalDelta
        FROM SkuStockLedger l
        WHERE l.sku.id IN :skuIds
        GROUP BY l.sku.id
        """)
    List<StockSum> sumDeltaBySkuIdIn(@Param("skuIds") List<Long> skuIds);

    interface StockSum {
        Long getSkuId();
        Long getTotalDelta();
    }

    Page<SkuStockLedger> findBySkuIdOrderByCreatedAtDesc(Long skuId, Pageable pageable);

    List<SkuStockLedger> findByRefTypeAndRefId(String refType, Long refId);
}
