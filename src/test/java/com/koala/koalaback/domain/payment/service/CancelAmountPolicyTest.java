package com.koala.koalaback.domain.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전액 취소와 부분 취소를 가르는 규칙.
 *
 * <p>PG 들은 요청에 금액이 없으면 전액 취소로, 있으면 부분 취소로 읽는다. 우리 장부에는 전액이든
 * 부분이든 금액을 적어야 하므로, <b>기록용 금액과 PG 에 보낼 금액을 따로 두어야 한다.</b>
 *
 * <p>구분하지 않으면 전액 환불이 전부 부분취소 요청으로 나간다. 나이스 샌드박스는 부분취소를
 * 아예 지원하지 않아 즉시 거절했고, 운영에서도 계좌이체·휴대폰처럼 부분취소가 안 되는 수단에서는
 * 같은 이유로 환불이 막힌다.
 */
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
