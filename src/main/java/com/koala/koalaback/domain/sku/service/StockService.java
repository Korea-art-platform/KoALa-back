package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.entity.SkuStockLedger;
import com.koala.koalaback.domain.sku.event.StockDepletedEvent;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.domain.sku.repository.SkuStockLedgerRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.infra.redis.StockCacheService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public int getStock(Long skuId) {
        return stockCacheService.getOrLoad(
                skuId,
                () -> stockLedgerRepository.sumDeltaBySkuId(skuId)
        );
    }

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
            Map<Long, Integer> loaded = new HashMap<>();
            missed.forEach(id -> loaded.put(id, 0));
            stockLedgerRepository.sumDeltaBySkuIdIn(missed).forEach(row ->
                    loaded.put(row.getSkuId(), row.getTotalDelta().intValue()));

            stockCacheService.setAll(loaded);
            stocks.putAll(loaded);
        }
        return stocks;
    }

    @Transactional
    public void initialize(Sku sku, int quantity, String memo) {
        record(sku, quantity, "INITIAL", null, null, memo);
        stockCacheService.evict(sku.getId());
    }

    @Transactional
    public void deduct(Long skuId, int quantity, String refType, Long refId) {
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

            eventPublisher.publishEvent(new StockDepletedEvent(
                    sku.getSkuCode(), sku.getName(),
                    sku.getArtist() != null ? sku.getArtist().getName() : null));
        }
    }

    @Transactional
    public void restore(Long skuId, int quantity, String refType, Long refId) {
        Sku sku = lockSku(skuId);
        record(sku, quantity, "CANCEL_RESTORE", refType, refId, null);
        stockCacheService.evict(skuId);
        reactivateIfBackInStock(sku, skuId);
    }

    @Transactional
    public void restoreByReturn(Long skuId, int quantity, Long orderItemId) {
        Sku sku = lockSku(skuId);
        record(sku, quantity, "RETURN", "order_items", orderItemId, null);
        stockCacheService.evict(skuId);
        reactivateIfBackInStock(sku, skuId);
    }

    @Transactional
    public void adminAdjust(Long skuId, int delta, String memo) {
        Sku sku = lockSku(skuId);
        record(sku, delta, "ADMIN_ADJUST", null, null, memo);
        stockCacheService.evict(skuId);

        int newStock = stockLedgerRepository.sumDeltaBySkuId(skuId);
        if (newStock <= 0) {
            sku.markOutOfStock();
        } else if ("OUT_OF_STOCK".equals(sku.getStatus())) {
            sku.markActive();
        }
        log.info("Admin stock adjust: skuId={}, delta={}, newStock={}",
                skuId, delta, newStock);
    }

    private Sku getSkuOrThrow(Long skuId) {
        return skuRepository.findById(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
    }

    private Sku lockSku(Long skuId) {
        Sku sku = getSkuOrThrow(skuId);
        entityManager.refresh(sku, LockModeType.PESSIMISTIC_WRITE);
        return sku;
    }

    private void reactivateIfBackInStock(Sku sku, Long skuId) {
        if (!"OUT_OF_STOCK".equals(sku.getStatus())) return;

        int current = stockLedgerRepository.sumDeltaBySkuId(skuId);
        if (current > 0) {
            sku.markActive();
        }
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
