package com.koala.koalaback.domain.payment.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 페이플 거래키 묶기·풀기.
 *
 * <p>다른 PG 는 tid 하나로 취소되지만 페이플은 <b>주문번호 + 결제일자</b>가 필요하다.
 * 일자를 승인 시점에 붙여 두지 않으면 나중에 날짜를 추측해야 하고, 추측이 틀리면
 * 환불이 실패가 아니라 <b>"환불됐는지 모르는" 상태</b>가 된다.
 *
 * <p>그래서 묶고 푸는 것이 정확히 역이어야 한다.
 */
@DisplayName("페이플 거래키")
class PayplePaymentProviderTest {

    @Test
    @DisplayName("주문번호와 결제일자를 묶는다")
    void packsOrderIdAndDate() {
        assertThat(PayplePaymentProvider.packTransactionId("ORD-1", "20260818153045"))
                .isEqualTo("ORD-1|20260818");
    }

    @Test
    @DisplayName("묶은 것을 그대로 되돌린다")
    void unpackIsInverseOfPack() {
        String packed = PayplePaymentProvider.packTransactionId("ORD-20260818-1", "20260818153045");
        String[] parts = PayplePaymentProvider.unpackTransactionId(packed);

        assertThat(parts[0]).isEqualTo("ORD-20260818-1");
        assertThat(parts[1]).isEqualTo("20260818");
    }

    @Test
    @DisplayName("주문번호에 구분자가 들어 있어도 날짜를 정확히 떼어낸다")
    void handlesSeparatorInsideOrderId() {
        // 마지막 구분자 기준으로 잘라야 앞쪽 값이 깨지지 않는다
        String[] parts = PayplePaymentProvider.unpackTransactionId("ORD|WEIRD|20260818");

        assertThat(parts[0]).isEqualTo("ORD|WEIRD");
        assertThat(parts[1]).isEqualTo("20260818");
    }

    @Test
    @DisplayName("결제 시각이 없으면 주문번호만 남는다 — 날짜를 지어내지 않는다")
    void withoutPayTimeKeepsOrderIdOnly() {
        assertThat(PayplePaymentProvider.packTransactionId("ORD-1", null)).isEqualTo("ORD-1");
        assertThat(PayplePaymentProvider.packTransactionId("ORD-1", "2026")).isEqualTo("ORD-1");
    }

    @Test
    @DisplayName("날짜가 없는 거래키는 날짜를 null 로 돌려준다 — 취소가 시도조차 되지 않게")
    void unpackWithoutDateReturnsNull() {
        String[] parts = PayplePaymentProvider.unpackTransactionId("ORD-1");

        assertThat(parts[0]).isEqualTo("ORD-1");
        assertThat(parts[1]).isNull();
    }

    @Test
    @DisplayName("null 이 들어와도 터지지 않는다")
    void handlesNull() {
        assertThat(PayplePaymentProvider.packTransactionId(null, "20260818153045")).isNull();

        String[] parts = PayplePaymentProvider.unpackTransactionId(null);
        assertThat(parts[0]).isNull();
        assertThat(parts[1]).isNull();
    }

    @Test
    @DisplayName("거래키가 컬럼 길이(100자)를 넘지 않는다")
    void fitsInColumn() {
        String longOrderId = "ORD-" + "9".repeat(60);

        assertThat(PayplePaymentProvider.packTransactionId(longOrderId, "20260818153045").length())
                .isLessThanOrEqualTo(100);
    }
}
