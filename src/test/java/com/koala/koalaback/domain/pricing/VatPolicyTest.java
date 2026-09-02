package com.koala.koalaback.domain.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("부가세 계산")
class VatPolicyTest {

    private final VatPolicy vat = new VatPolicy(null);
    private static final Set<String> EXEMPT = Set.of("MAIN_3");

    private BigDecimal won(String v) { return new BigDecimal(v); }

    @Test
    @DisplayName("한정판·오픈에디션에는 10% 를 붙인다")
    void taxable() {
        // 대표님이 확인해 준 값 — 한정판 30만이 33만, 오픈에디션 20만이 22만.
        assertThat(vat.grossOf(won("300000"), "LIMITED", EXEMPT)).isEqualByComparingTo("330000");
        assertThat(vat.grossOf(won("200000"), "MAIN_2", EXEMPT)).isEqualByComparingTo("220000");
        assertThat(vat.grossOf(won("20000"), "MAIN_2", EXEMPT)).isEqualByComparingTo("22000");
    }

    @Test
    @DisplayName("원작에는 붙이지 않는다")
    void exempt() {
        assertThat(vat.vatOf(won("3000000"), "MAIN_3", EXEMPT)).isEqualByComparingTo("0");
        assertThat(vat.grossOf(won("3000000"), "MAIN_3", EXEMPT)).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("세액은 원 단위로 반올림한다")
    void rounding() {
        // PG 는 정수만 받는다. 소수점이 남으면 화면의 값과 결제 금액이 어긋난다.
        assertThat(vat.vatOf(won("12345"), "MAIN_2", EXEMPT)).isEqualByComparingTo("1235");
        assertThat(vat.grossOf(won("12345"), "MAIN_2", EXEMPT)).isEqualByComparingTo("13580");
        assertThat(vat.vatOf(won("12345"), "MAIN_2", EXEMPT).stripTrailingZeros().scale())
                .isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("분류가 없으면 과세로 본다")
    void unknownCategoryIsTaxable() {
        // 면세는 표시해 둔 분류에만 준다. 모르는 값이 면세로 새면 세금을 덜 걷는다.
        assertThat(vat.vatOf(won("100000"), null, EXEMPT)).isEqualByComparingTo("10000");
        assertThat(vat.vatOf(won("100000"), "무엇인지_모름", EXEMPT)).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("배송비에 든 세액은 1/11 이다")
    void shippingVatIsIncluded() {
        // 배송비 3,000원은 부가세가 포함된 금액이다. 10% 로 잡으면 300원이 되어
        // 공급가액이 2,700원으로 어긋난다. 포함된 세액은 1/11 인 273원이다.
        assertThat(vat.vatIncludedIn(won("3000"))).isEqualByComparingTo("273");
        assertThat(vat.vatIncludedIn(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(vat.vatIncludedIn(null)).isEqualByComparingTo("0");
    }
}
