package com.koala.koalaback.domain.sku.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sku 수정 — 위치 인자 배선")
class SkuUpdateTest {
    private Sku baseSku() {
        return Sku.builder()
                .skuCode("S-1")
                .name("닥스훈트")
                .model("닥스훈트")
                .subModelName("적색")
                .slug("dakseuhunteu-red")
                .mainCategory(Sku.MAIN_NORMAL)
                .genre("ART_TOY")
                .listPrice(BigDecimal.valueOf(80_000))
                .widthCm(BigDecimal.valueOf(10))
                .heightCm(BigDecimal.valueOf(20))
                .depthCm(BigDecimal.valueOf(5))
                .weightKg(BigDecimal.ONE)
                .build();
    }

    @Test
    @DisplayName("모델·세부모델명이 수정된다")
    void updatesModelAndSubModel() {
        Sku sku = baseSku();

        update(sku, "닥스훈트", "청색", BigDecimal.valueOf(11), BigDecimal.valueOf(21),
                BigDecimal.valueOf(6), BigDecimal.valueOf(2));

        assertThat(sku.getModel()).isEqualTo("닥스훈트");
        assertThat(sku.getSubModelName()).isEqualTo("청색");
    }

    @Test
    @DisplayName("치수가 수정된다 — 이전에는 update 가 치수를 무시했다")
    void updatesDimensions() {
        Sku sku = baseSku();

        update(sku, "m", "s", BigDecimal.valueOf(11), BigDecimal.valueOf(22),
                BigDecimal.valueOf(6), BigDecimal.valueOf(3));

        assertThat(sku.getWidthCm()).isEqualByComparingTo("11");
        assertThat(sku.getHeightCm()).isEqualByComparingTo("22");
        assertThat(sku.getDepthCm()).isEqualByComparingTo("6");
        assertThat(sku.getWeightKg()).isEqualByComparingTo("3");
    }

    private void update(Sku sku, String model, String subModel,
                        BigDecimal w, BigDecimal h, BigDecimal d, BigDecimal kg) {
        sku.update("닥스훈트", "dakseuhunteu-red", "설명",
                "ARTWORK", Sku.MAIN_NORMAL, "ART_TOY", "레진",
                null, null, null,
                BigDecimal.valueOf(80_000), null, null,
                null, null,
                null,
                model, subModel,
                w, h, d, kg);
    }
}
