package com.koala.koalaback.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheService {
    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(Long skuId, int stock) {
        try {
            redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + skuId, stock, TTL);
            log.debug("Stock cache set: skuId={}, stock={}", skuId, stock);
        } catch (Exception e) {
            log.warn("Stock cache set failed: skuId={}", skuId, e);
        }
    }

    public Integer get(Long skuId) {
        try {
            Object val = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + skuId);
            return val != null ? (Integer) val : null;
        } catch (Exception e) {
            log.warn("Stock cache get failed: skuId={}", skuId, e);
            return null;
        }
    }

    public void setAll(Map<Long, Integer> stocks) {
        stocks.forEach(this::set);
    }

    public Map<Long, Integer> getAll(List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<String> keys = skuIds.stream()
                    .map(id -> STOCK_KEY_PREFIX + id)
                    .toList();
            List<Object> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Map.of();
            }
            Map<Long, Integer> hits = new HashMap<>();
            for (int i = 0; i < skuIds.size(); i++) {
                Object val = values.get(i);
                if (val != null) {
                    hits.put(skuIds.get(i), (Integer) val);
                }
            }
            log.debug("Stock cache mget: requested={}, hit={}", skuIds.size(), hits.size());
            return hits;
        } catch (Exception e) {
            log.warn("Stock cache mget failed: size={}", skuIds.size(), e);
            return Map.of();
        }
    }

    public void evict(Long skuId) {
        try {
            redisTemplate.delete(STOCK_KEY_PREFIX + skuId);
            log.debug("Stock cache evict: skuId={}", skuId);
        } catch (Exception e) {
            log.warn("Stock cache evict failed: skuId={}", skuId, e);
        }
    }

    public int getOrLoad(Long skuId, java.util.function.Supplier<Integer> loader) {
        Integer cached = get(skuId);
        if (cached != null) {
            log.debug("Stock cache hit: skuId={}, stock={}", skuId, cached);
            return cached;
        }
        int stock = loader.get();
        set(skuId, stock);
        log.debug("Stock cache miss → loaded: skuId={}, stock={}", skuId, stock);
        return stock;
    }
}
