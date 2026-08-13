package com.koala.koalaback.domain.sku.event;

/**
 * 재고가 0이 되어 상품이 판매 중지로 전환됐다.
 *
 * <p>알림 전용 내부 이벤트다.
 *
 * <p>차감 트랜잭션 안에서 발행하고 <b>커밋 이후</b>에 처리한다. 트랜잭션이 롤백되면
 * 재고는 그대로인데 "품절됐다"는 알림만 나가기 때문이다.
 */
public record StockDepletedEvent(
        String skuCode,
        String skuName,
        String artistName
) {}
