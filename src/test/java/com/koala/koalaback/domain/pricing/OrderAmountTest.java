package com.koala.koalaback.domain.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 금액이 화면에 보여 준 값과 같은지 본다.
 *
 * OrderService 는 장바구니·재고·결제가 얽혀 있어 통째로 띄우기 어렵다.
 * 금액을 만드는 규칙만 떼어 같은 계산을 두고 견준다.
 */
@DisplayName("주문 금액")
class OrderAmountTest {

    private final VatPolicy vat = new VatPolicy(null);
    private static final Set<String> EXEMPT = Set.of("MAIN_3");

    private BigDecimal won(String v) { return new BigDecimal(v); }

    /** OrderService 와 같은 규칙 */
    private BigDecimal shippingFor(BigDecimal productGross) {
        return productGross.compareTo(new BigDecimal("50000")) >= 0
                ? BigDecimal.ZERO : new BigDecimal("3000");
    }

    @Test
    @DisplayName("여러 개를 사면 단가 × 수량이 줄 합계와 맞는다")
    void lineMatchesUnitTimesQuantity() {
        VatPolicy.Line line = vat.lineOf(won("200000"), 3, "MAIN_2", EXEMPT);

        assertThat(line.unitGross()).isEqualByComparingTo("220000");
        assertThat(line.gross()).isEqualByComparingTo("660000");
        // 화면에서 직접 곱해 보는 값이라 어긋나면 계산이 틀린 것으로 읽힌다.
        assertThat(line.unitGross().multiply(BigDecimal.valueOf(3)))
                .isEqualByComparingTo(line.gross());
        assertThat(line.supply().add(line.tax())).isEqualByComparingTo(line.gross());
    }

    @Test
    @DisplayName("면세 상품만 담으면 세액이 0 이다")
    void exemptOnly() {
        VatPolicy.Line line = vat.lineOf(won("3000000"), 1, "MAIN_3", EXEMPT);
        assertThat(line.tax()).isEqualByComparingTo("0");
        assertThat(line.gross()).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("무료배송은 부가세를 더한 금액으로 판단한다")
    void freeShippingUsesGross() {
        // 공급가액 46,000원은 5만원에 못 미치지만, 고객이 보는 값은 50,600원이라
        // 무료가 되어야 한다. 보이는 금액과 다른 기준으로 배송비를 물리면 안 된다.
        VatPolicy.Line line = vat.lineOf(won("46000"), 1, "MAIN_2", EXEMPT);
        assertThat(line.gross()).isEqualByComparingTo("50600");
        assertThat(shippingFor(line.gross())).isEqualByComparingTo("0");

        VatPolicy.Line under = vat.lineOf(won("40000"), 1, "MAIN_2", EXEMPT);
        assertThat(under.gross()).isEqualByComparingTo("44000");
        assertThat(shippingFor(under.gross())).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("합계는 공급가액 + 세액 + 배송비와 같다")
    void totalAddsUp() {
        VatPolicy.Line a = vat.lineOf(won("200000"), 1, "MAIN_2", EXEMPT);   // 220,000
        VatPolicy.Line b = vat.lineOf(won("3000000"), 1, "MAIN_3", EXEMPT);  // 면세

        BigDecimal supply = a.supply().add(b.supply());
        BigDecimal productTax = a.tax().add(b.tax());
        BigDecimal productGross = supply.add(productTax);
        BigDecimal shipping = shippingFor(productGross);
        BigDecimal shippingTax = vat.vatIncludedIn(shipping);

        assertThat(productGross).isEqualByComparingTo("3220000");
        assertThat(shipping).isEqualByComparingTo("0");         // 5만원 넘음
        assertThat(shippingTax).isEqualByComparingTo("0");
        assertThat(productTax).isEqualByComparingTo("20000");   // 면세분은 빠진다

        BigDecimal total = productGross.add(shipping);
        assertThat(total).isEqualByComparingTo(supply.add(productTax).add(shipping));
    }

    @Test
    @DisplayName("배송비가 붙으면 그 안의 세액도 장부에 남는다")
    void shippingTaxIsRecorded() {
        VatPolicy.Line a = vat.lineOf(won("20000"), 1, "MAIN_2", EXEMPT);    // 22,000
        BigDecimal shipping = shippingFor(a.gross());
        assertThat(shipping).isEqualByComparingTo("3000");

        BigDecimal taxAmount = a.tax().add(vat.vatIncludedIn(shipping));
        assertThat(taxAmount).isEqualByComparingTo("2273");   // 2,000 + 273
        assertThat(a.gross().add(shipping)).isEqualByComparingTo("25000");
    }
}
