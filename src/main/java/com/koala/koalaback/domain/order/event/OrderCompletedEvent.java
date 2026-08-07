package com.koala.koalaback.domain.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 주문 완료(결제 승인) 이벤트 — 토픽 {@code order.completed}
 *
 * <h3>스키마 버전 관리</h3>
 * <p>{@link #schemaVersion} 을 담아 컨슈머가 자신이 아는 버전인지 판단할 수 있게 한다.
 * 변경은 <b>추가만</b> 허용한다(필드 추가 O, 삭제·의미 변경 X).
 * 컨슈머 역직렬화는 모르는 필드를 무시하도록 설정되어 있으므로,
 * 필드를 추가해도 구버전 컨슈머가 깨지지 않는다.
 * 호환되지 않는 변경이 필요하면 새 토픽({@code order.completed.v2})을 쓴다.
 *
 * <h3>페이로드에 스냅샷을 담는 이유</h3>
 * <p>컨슈머는 트랜잭션 밖·다른 시점에 돌기 때문에 엔티티를 다시 조회하면
 * 그 사이 바뀐 값을 보게 되고, 지연로딩 컬렉션에도 접근할 수 없다.
 * 그래서 메일 발송에 필요한 값을 발행 시점 스냅샷으로 전부 담는다.
 *
 * @param eventId       이벤트 고유 ID — 컨슈머 멱등 처리(중복 수신 판별)용
 * @param orderId       파티션 키로 사용 — 같은 주문의 이벤트는 같은 파티션에 들어가 순서가 보장된다
 */
public record OrderCompletedEvent(
        String eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,

        Long orderId,
        String orderNo,
        Long userId,

        String ordererName,
        String ordererEmail,

        BigDecimal productAmount,
        BigDecimal shippingAmount,
        BigDecimal totalAmount,

        List<Item> items
) {
    public static final String TOPIC = "order.completed";
    public static final String EVENT_TYPE = "order.completed";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public record Item(String skuCode, String skuName, int quantity, BigDecimal lineAmount) {}

    public static OrderCompletedEvent of(Long orderId, String orderNo, Long userId,
                                         String ordererName, String ordererEmail,
                                         BigDecimal productAmount, BigDecimal shippingAmount,
                                         BigDecimal totalAmount, List<Item> items) {
        return new OrderCompletedEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                CURRENT_SCHEMA_VERSION,
                Instant.now(),
                orderId, orderNo, userId,
                ordererName, ordererEmail,
                productAmount, shippingAmount, totalAmount,
                items);
    }

    /** 파티션 키 — 주문 단위 순서 보장 */
    public String partitionKey() {
        return String.valueOf(orderId);
    }
}
