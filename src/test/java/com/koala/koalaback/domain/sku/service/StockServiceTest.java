package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.domain.sku.repository.SkuStockLedgerRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.infra.redis.StockCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {
    @InjectMocks
    private StockService stockService;

    @Mock private SkuStockLedgerRepository stockLedgerRepository;
    @Mock private SkuRepository skuRepository;
    @Mock private StockCacheService stockCacheService;

    @Mock private EntityManager entityManager;

    @Test
    @DisplayName("재고 차감 성공")
    void deduct_success() {
        Long skuId = 1L;
        Sku sku = mock(Sku.class);

        given(skuRepository.findByIdForUpdate(skuId)).willReturn(Optional.of(sku));
        given(stockLedgerRepository.sumDeltaBySkuId(skuId)).willReturn(10);
        given(stockLedgerRepository.save(any())).willReturn(null);

        stockService.deduct(skuId, 3, "order_items", null);

        then(stockLedgerRepository).should().save(any());
        then(stockCacheService).should().evict(skuId);
    }

    @Test
    @DisplayName("재고 차감 실패 — 재고 부족")
    void deduct_fail_out_of_stock() {
        Long skuId = 1L;
        Sku sku = mock(Sku.class);

        given(skuRepository.findByIdForUpdate(skuId)).willReturn(Optional.of(sku));
        given(stockLedgerRepository.sumDeltaBySkuId(skuId)).willReturn(2);

        assertThatThrownBy(() -> stockService.deduct(skuId, 5, "order_items", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SKU_OUT_OF_STOCK));
    }

    @Test
    @DisplayName("재고 복원 성공")
    void restore_success() {
        Long skuId = 1L;
        Sku sku = mock(Sku.class);
        given(sku.getStatus()).willReturn("ACTIVE");

        given(skuRepository.findById(skuId)).willReturn(Optional.of(sku));
        given(stockLedgerRepository.save(any())).willReturn(null);

        stockService.restore(skuId, 3, "order_items", 1L);

        then(stockLedgerRepository).should().save(any());
        then(stockCacheService).should().evict(skuId);

        then(entityManager).should().refresh(sku, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("품절 상태에서 복원되면 다시 판매 상태로 돌아간다")
    void restore_reactivatesOutOfStockSku() {
        Long skuId = 1L;
        Sku sku = mock(Sku.class);
        given(sku.getStatus()).willReturn("OUT_OF_STOCK");

        given(skuRepository.findById(skuId)).willReturn(Optional.of(sku));
        given(stockLedgerRepository.save(any())).willReturn(null);
        given(stockLedgerRepository.sumDeltaBySkuId(skuId)).willReturn(3);

        stockService.restore(skuId, 3, "order_items", 1L);

        then(sku).should().markActive();
    }

    @Test
    @DisplayName("판매중단 상품은 복원돼도 다시 판매 상태가 되지 않는다")
    void restore_doesNotReviveDiscontinuedSku() {
        Long skuId = 1L;
        Sku sku = mock(Sku.class);
        given(sku.getStatus()).willReturn("DISCONTINUED");

        given(skuRepository.findById(skuId)).willReturn(Optional.of(sku));
        given(stockLedgerRepository.save(any())).willReturn(null);

        stockService.restore(skuId, 3, "order_items", 1L);

        then(sku).should(never()).markActive();
    }
}
