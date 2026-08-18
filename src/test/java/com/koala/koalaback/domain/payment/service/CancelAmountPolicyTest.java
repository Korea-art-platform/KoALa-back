package com.koala.koalaback.domain.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("환불 금액 정책")
class CancelAmountPolicyTest {
    private PaymentTransactionService.CancelContext context(String approved, String cancel) {
        BigDecimal approvedAmount = new BigDecimal(approved);
        BigDecimal cancelAmount = new BigDecimal(cancel);
        boolean partial = cancelAmount.compareTo(approvedAmount) < 0;
        return new PaymentTransactionService.CancelContext(
                1L, "NICEPAY", "tid_1", cancelAmount, partial);
    }

    @Test
    @DisplayName("전액 환불이면 PG 에 금액을 보내지 않는다")
    void fullCancelSendsNoAmount() {
        var ctx = context("450000", "450000");

        assertThat(ctx.partial()).isFalse();
        assertThat(ctx.amountForProvider()).as("금액을 실으면 부분취소 요청이 된다").isNull();
        assertThat(ctx.cancelAmount()).as("장부에는 금액을 남긴다")
                .isEqualByComparingTo("450000");
    }

    @Test
    @DisplayName("부분 환불이면 그 금액을 보낸다")
    void partialCancelSendsAmount() {
        var ctx = context("450000", "100000");

        assertThat(ctx.partial()).isTrue();
        assertThat(ctx.amountForProvider()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("소수점 표기가 달라도 같은 금액이면 전액으로 본다")
    void scaleDifferenceIsStillFullCancel() {
        var ctx = context("450000.00", "450000");

        assertThat(ctx.partial()).as("450000.00 과 450000 은 같은 금액이다").isFalse();
        assertThat(ctx.amountForProvider()).isNull();
    }

    @Test
    @DisplayName("1원만 적어도 부분 환불이다")
    void oneWonLessIsPartial() {
        var ctx = context("450000", "449999");

        assertThat(ctx.partial()).isTrue();
        assertThat(ctx.amountForProvider()).isEqualByComparingTo("449999");
    }
}
