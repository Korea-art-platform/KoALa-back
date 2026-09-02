package com.koala.koalaback.domain.pricing;

import com.koala.koalaback.domain.category.entity.SkuCategory;
import com.koala.koalaback.domain.category.repository.SkuCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 부가세를 계산하는 단 한 곳.
 *
 * 상품에 저장된 가격은 공급가액이다. 화면에 보이는 값과 실제로 결제되는 값은
 * 여기에 부가세를 더한 금액이다. 두 값이 다르면 안 되므로, 표시하는 쪽과
 * 결제하는 쪽이 모두 이 클래스를 거친다.
 *
 * 원작처럼 면세로 표시된 대분류에는 붙이지 않는다. 어느 분류가 면세인지는
 * sku_categories.tax_exempt 에 있고 어드민에서 바꾼다 — 세무 판단이라
 * 분류가 늘 때마다 배포하게 두면 안 된다.
 *
 * 배송비는 부가세가 포함된 금액으로 본다. 고객이 내는 3,000원은 그대로 두고,
 * 그 안에 들어 있는 세액만 따로 뽑아 장부에 남긴다.
 */
@Component
@RequiredArgsConstructor
public class VatPolicy {

    /** 부가세율 10% */
    public static final BigDecimal RATE = new BigDecimal("0.10");

    private final SkuCategoryRepository categoryRepository;

    /** 면세로 표시된 대분류 코드 */
    @Transactional(readOnly = true)
    public Set<String> exemptMainCategories() {
        return categoryRepository.findAllByOrderByTypeAscSortOrderAsc().stream()
                .filter(SkuCategory::isMain)
                .filter(SkuCategory::isTaxExempt)
                .map(SkuCategory::getCode)
                .collect(Collectors.toSet());
    }

    public boolean isExempt(String mainCategory, Set<String> exempt) {
        return mainCategory != null && exempt.contains(mainCategory);
    }

    /**
     * 공급가액에 붙는 세액. 면세면 0.
     *
     * 원 단위로 반올림한다. 소수점이 남으면 결제 금액과 화면의 값이 1원씩
     * 어긋나고, PG 는 정수만 받는다.
     */
    public BigDecimal vatOf(BigDecimal supply, String mainCategory, Set<String> exempt) {
        if (supply == null || isExempt(mainCategory, exempt)) return BigDecimal.ZERO;
        return supply.multiply(RATE).setScale(0, RoundingMode.HALF_UP);
    }

    /** 화면에 보이고 실제로 결제되는 금액 = 공급가액 + 세액 */
    public BigDecimal grossOf(BigDecimal supply, String mainCategory, Set<String> exempt) {
        if (supply == null) return null;
        return supply.add(vatOf(supply, mainCategory, exempt));
    }

    /**
     * 여러 개를 살 때의 금액.
     *
     * 세액은 줄 합계가 아니라 단가에서 반올림한다. 줄 합계에서 반올림하면
     * "단가 × 수량"이 줄 합계와 1원씩 어긋나 보인다. 화면에서 직접 곱해 보는
     * 값이라 맞지 않으면 계산이 틀린 것으로 읽힌다.
     */
    public Line lineOf(BigDecimal unitSupply, int quantity, String mainCategory, Set<String> exempt) {
        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal unitVat = vatOf(unitSupply, mainCategory, exempt);
        return new Line(
                unitSupply.add(unitVat),
                unitSupply.multiply(qty),
                unitVat.multiply(qty),
                unitSupply.add(unitVat).multiply(qty));
    }

    /**
     * @param unitGross  화면에 보이는 단가
     * @param supply     공급가액 합계
     * @param tax        세액 합계
     * @param gross      결제 금액 합계
     */
    public record Line(BigDecimal unitGross, BigDecimal supply, BigDecimal tax, BigDecimal gross) {}

    /**
     * 부가세가 포함된 금액에서 세액만 뽑는다. 배송비에 쓴다.
     *
     * 3,000원이면 273원이다. 포함된 금액의 1/11 이지 10% 가 아니다 —
     * 10% 로 잡으면 300원이 되어 공급가액이 2,700원으로 어긋난다.
     */
    public BigDecimal vatIncludedIn(BigDecimal gross) {
        if (gross == null || gross.signum() == 0) return BigDecimal.ZERO;
        return gross.divide(new BigDecimal("11"), 0, RoundingMode.HALF_UP);
    }
}
