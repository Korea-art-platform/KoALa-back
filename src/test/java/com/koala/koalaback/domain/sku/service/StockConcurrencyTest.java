package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("재고 차감 동시성")
class StockConcurrencyTest extends IntegrationTestSupport {
    @Autowired private StockService stockService;
    @Autowired private SkuRepository skuRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                DELETE FROM sku_stock_ledger
                WHERE sku_id IN (SELECT id FROM skus WHERE sku_code LIKE 'CTEST-%')
                """);
        jdbcTemplate.update("DELETE FROM skus WHERE sku_code LIKE 'CTEST-%'");
        jdbcTemplate.update("DELETE FROM artists WHERE artist_code LIKE 'CTEST-%'");
    }

    @Test
    @DisplayName("재고 1개에 10개 스레드가 동시 주문 — 1건만 성공하고 재고는 0")
    void deduct_concurrently_sellsExactlyOne() throws Exception {
        Long skuId = givenSkuWithStock(1);

        ExecutionResult result = runConcurrently(
                Collections.nCopies(10, () -> stockService.deduct(skuId, 1, "order_items", null)));

        assertThat(result.success()).as("성공 주문 수").isEqualTo(1);
        assertThat(result.outOfStock()).as("재고 부족으로 거절된 수").isEqualTo(9);
        assertThat(result.otherFailure()).as("예상 못 한 실패").isZero();
        assertThat(stockOf(skuId)).as("최종 재고 — 음수면 오버셀링").isZero();
    }

    @Test
    @DisplayName("재고 5개에 20개 스레드가 동시 주문 — 정확히 5건만 성공")
    void deduct_concurrently_sellsExactlyAvailableStock() throws Exception {
        Long skuId = givenSkuWithStock(5);

        ExecutionResult result = runConcurrently(
                Collections.nCopies(20, () -> stockService.deduct(skuId, 1, "order_items", null)));

        assertThat(result.success()).as("성공 주문 수").isEqualTo(5);
        assertThat(result.outOfStock()).as("재고 부족으로 거절된 수").isEqualTo(15);
        assertThat(result.otherFailure()).as("예상 못 한 실패").isZero();
        assertThat(stockOf(skuId)).as("최종 재고 — 음수면 오버셀링").isZero();
    }

    @Test
    @DisplayName("차감과 복원이 동시에 일어나도 재고 합계는 정확하고 상태와 모순되지 않는다")
    void deductAndRestore_concurrently_keepStockConsistent() throws Exception {
        int initialStock = 5;
        int deductCount = 10;
        int restoreCount = 10;
        Long skuId = givenSkuWithStock(initialStock);

        AtomicInteger deducted = new AtomicInteger();
        AtomicInteger restored = new AtomicInteger();

        List<Runnable> actions = new ArrayList<>();
        for (int i = 0; i < deductCount; i++) {
            actions.add(() -> {
                stockService.deduct(skuId, 1, "order_items", null);
                deducted.incrementAndGet();
            });
        }
        for (int i = 0; i < restoreCount; i++) {
            actions.add(() -> {
                stockService.restore(skuId, 1, "order_items", null);
                restored.incrementAndGet();
            });
        }

        ExecutionResult result = runConcurrently(actions);

        int finalStock = stockOf(skuId);
        int expectedStock = initialStock - deducted.get() + restored.get();

        assertThat(finalStock)
                .as("원장은 append-only 이므로 합계는 성공한 차감/복원과 정확히 일치해야 한다 (%s)", result)
                .isEqualTo(expectedStock);
        assertThat(finalStock).as("최종 재고 — 음수면 오버셀링").isNotNegative();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM skus WHERE id = ?", String.class, skuId);
        if (finalStock > 0) {
            assertThat(status)
                    .as("재고가 %d 남았는데 status 가 OUT_OF_STOCK 이면 판매 불가로 잘못 고착된 것", finalStock)
                    .isNotEqualTo("OUT_OF_STOCK");
        }
    }

    private record ExecutionResult(int success, int outOfStock, int otherFailure) {
        @Override
        public String toString() {
            return "성공=%d, 재고부족=%d, 기타실패=%d".formatted(success, outOfStock, otherFailure);
        }
    }

    private ExecutionResult runConcurrently(List<Runnable> actions) throws InterruptedException {
        int threadCount = actions.size();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger outOfStock = new AtomicInteger();
        AtomicInteger otherFailure = new AtomicInteger();

        try {
            for (Runnable action : actions) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        action.run();
                        success.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.SKU_OUT_OF_STOCK) {
                            outOfStock.incrementAndGet();
                        } else {
                            otherFailure.incrementAndGet();
                        }
                    } catch (Exception e) {
                        otherFailure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(30, TimeUnit.SECONDS)).as("스레드 준비").isTrue();
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).as("모든 스레드 종료").isTrue();
        } finally {
            executor.shutdownNow();
        }
        return new ExecutionResult(success.get(), outOfStock.get(), otherFailure.get());
    }

    private int stockOf(Long skuId) {
        Integer sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(delta), 0) FROM sku_stock_ledger WHERE sku_id = ?",
                Integer.class, skuId);
        return sum != null ? sum : 0;
    }

    private Long givenSkuWithStock(int initialStock) {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        Artist artist = artistRepository.save(Artist.builder()
                .artistCode("CTEST-" + uid)
                .name("동시성 테스트 작가")
                .slug("ctest-artist-" + uid)
                .build());

        Sku sku = Sku.builder()
                .skuCode("CTEST-" + uid)
                .artist(artist)
                .name("동시성 테스트 상품")
                .slug("ctest-sku-" + uid)
                .skuType("ARTWORK")
                .mainCategory(Sku.MAIN_NORMAL)
                .genre("ART_TOY")
                .currency("KRW")
                .listPrice(BigDecimal.valueOf(10_000))
                .build();
        sku.publish();
        skuRepository.save(sku);

        stockService.initialize(sku, initialStock, "동시성 테스트 초기 재고");
        return sku.getId();
    }
}
