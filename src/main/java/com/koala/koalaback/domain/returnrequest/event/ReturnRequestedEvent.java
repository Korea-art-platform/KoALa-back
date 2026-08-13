package com.koala.koalaback.domain.returnrequest.event;

/**
 * 고객이 반품·교환을 신청했다.
 *
 * <p>알림 전용 내부 이벤트다. Kafka 로 나가지 않는다.
 *
 * <p>필요한 값을 전부 담아 보내는 이유는 수신 쪽이 커밋 이후에 동작하기 때문이다.
 * 그 시점에는 트랜잭션이 닫혀 있어 지연로딩으로 주문·회원을 다시 읽을 수 없다.
 */
public record ReturnRequestedEvent(
        String returnNo,
        String orderNo,
        String returnType,
        String reason,
        String ordererName
) {}
