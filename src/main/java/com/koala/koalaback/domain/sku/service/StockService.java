package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.entity.SkuStockLedger;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.domain.sku.repository.SkuStockLedgerRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.infra.redis.StockCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final SkuStockLedgerRepository stockLedgerRepository;
    private final SkuRepository skuRepository;
    private final StockCacheService stockCacheService;

    /**
     * 재고 조회 — Redis 캐시 우선, 없으면 DB 집계
     */
    @Transactional(readOnly = true)
    public int getStock(Long skuId) {
        return stockCacheService.getOrLoad(
                skuId,
                () -> stockLedgerRepository.sumDeltaBySkuId(skuId)
        );
    }

    /**
     * 재고 일괄 조회 — 목록 조회에서 SKU 당 1쿼리(N+1) 를 없애기 위한 배치 버전.
     *
     * <p>Redis MGET 으로 먼저 훑고, 캐시 미스분만 원장 SUM 을 GROUP BY 로 한 번에 집계한다.
     * 즉 캐시 히트면 DB 쿼리 0회, 전부 미스여도 1회.
     *
     * @return skuId → 재고 수량 (요청한 모든 skuId 가 키로 채워진다)
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> getStocks(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> stocks = new HashMap<>(stockCacheService.getAll(skuIds));

        List<Long> missed = skuIds.stream()
                .filter(id -> !stocks.containsKey(id))
                .toList();
        if (!missed.isEmpty()) {
            // 원장에 행이 없는 SKU 는 GROUP BY 결과에 안 나오므로 0 으로 선채움
            Map<Long, Integer> loaded = new HashMap<>();
            missed.forEach(id -> loaded.put(id, 0));
            stockLedgerRepository.sumDeltaBySkuIdIn(missed).forEach(row ->
                    loaded.put(row.getSkuId(), row.getTotalDelta().intValue()));

            stockCacheService.setAll(loaded);
            stocks.putAll(loaded);
        }
        return stocks;
    }

    /**
     * 초기 재고 설정
     */
    @Transactional
    public void initialize(Sku sku, int quantity, String memo) {
        record(sku, quantity, "INITIAL", null, null, memo);
        stockCacheService.evict(sku.getId());
    }

    /**
     * 주문 시 재고 차감 — 부족하면 예외.
     *
     * <p>비관적 락(SELECT ... FOR UPDATE)으로 동시 주문 Race Condition을 방지합니다.
     * 같은 SKU에 대한 동시 요청은 이 트랜잭션이 커밋/롤백될 때까지 대기합니다.
     */
    @Transactional
    public void deduct(Long skuId, int quantity, String refType, Long refId) {
        // 비관적 락으로 Sku row를 잠근 뒤 재고 집계
        Sku sku = skuRepository.findByIdForUpdate(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));

        int current = stockLedgerRepository.sumDeltaBySkuId(skuId);
        if (current < quantity) {
            throw new BusinessException(ErrorCode.SKU_OUT_OF_STOCK);
        }
        record(sku, -quantity, "PURCHASE", refType, refId, null);
        stockCacheService.evict(skuId);

        if (current - quantity == 0) {
            sku.markOutOfStock();
        }
    }

    /**
     * 주문 취소 시 재고 복원
     */
    @Transactional
    public void restore(Long skuId, int quantity, String refType, Long refId) {
        Sku sku = getSkuOrThrow(skuId);
        record(sku, quantity, "CANCEL_RESTORE", refType, refId, null);
        stockCacheService.evict(skuId);

        if ("OUT_OF_STOCK".equals(sku.getStatus())) {
            sku.markActive();
        }
    }

    /**
     * 반품 시 재고 복원
     */
    @Transactional
    public void restoreByReturn(Long skuId, int quantity, Long orderItemId) {
        Sku sku = getSkuOrThrow(skuId);
        record(sku, quantity, "RETURN", "order_items", orderItemId, null);
        stockCacheService.evict(skuId);

        if ("OUT_OF_STOCK".equals(sku.getStatus())) {
            sku.markActive();
        }
    }

    /**
     * 어드민 수동 재고 조정
     */
    @Transactional
    public void adminAdjust(Long skuId, int delta, String memo) {
        Sku sku = getSkuOrThrow(skuId);
        record(sku, delta, "ADMIN_ADJUST", null, null, memo);
        stockCacheService.evict(skuId);

        int newStock = getStock(skuId);
        if (newStock <= 0) {
            sku.markOutOfStock();
        } else if ("OUT_OF_STOCK".equals(sku.getStatus())) {
            sku.markActive();
        }
        log.info("Admin stock adjust: skuId={}, delta={}, newStock={}",
                skuId, delta, newStock);
    }

    // ── Private helpers ───────────────────────────────────

    private Sku getSkuOrThrow(Long skuId) {
        return skuRepository.findById(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
    }

    private void record(Sku sku, int delta, String reason,
                        String refType, Long refId, String memo) {
        stockLedgerRepository.save(SkuStockLedger.builder()
                .sku(sku)
                .delta(delta)
                .reason(reason)
                .refType(refType)
                .refId(refId)
                .memo(memo)
                .build());
    }
}