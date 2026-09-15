package com.koala.koalaback.domain.sku.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("상품명·슬러그 — 종과 모델로 만든다")
class SkuNamingTest {

    @Test
    @DisplayName("모델에 종 이름이 들어 있으면 모델 이름만 쓴다")
    void modelContainsSpecies() {
        assertThat(SkuService.buildName("해피토마", "빨강색 해피토마")).isEqualTo("빨강색 해피토마");
    }

    @Test
    @DisplayName("모델에 종 이름이 없으면 종을 앞에 붙인다")
    void modelWithoutSpecies() {
        assertThat(SkuService.buildName("순정남", "블루")).isEqualTo("순정남 블루");
    }

    @Test
    @DisplayName("모델이 비면 종 이름을 쓴다")
    void blankModel() {
        assertThat(SkuService.buildName("해피토마", " ")).isEqualTo("해피토마");
    }

    @Test
    @DisplayName("슬러그도 같은 규칙으로 영문명에서 만든다")
    void slugFollowsSameRule() {
        assertThat(SkuService.buildSlug("HappyToma", "Red HappyToma")).isEqualTo("red-happytoma");
        assertThat(SkuService.buildSlug("sunjeongnam", "blue")).isEqualTo("sunjeongnam-blue");
    }

    @Test
    @DisplayName("영문명이 모두 비면 item 으로 만든다")
    void emptySlug() {
        assertThat(SkuService.buildSlug(null, "")).isEqualTo("item");
    }
}
