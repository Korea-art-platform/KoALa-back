package com.koala.koalaback.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@code payment_events.payload_json} 에 넣을 값을 다듬는다.
 *
 * <h3>왜 필요한가</h3>
 * <p>이 칼럼은 MySQL {@code JSON} 타입이다. PG 응답 본문을 그대로 넣으려고 그렇게 잡았는데,
 * 실패 경로에서는 <b>사람이 읽는 문장</b>을 그대로 넣고 있었다. 예를 들면
 * {@code "부분취소는 운영 환경에서 이용 가능"} 같은 값이다. 이건 JSON 이 아니라서 MySQL 이 거부하고,
 * 기록을 남기려던 트랜잭션이 통째로 뒤집힌다.
 *
 * <p>결과가 고약하다. <b>PG 가 거절한 사실 자체를 저장하지 못한다.</b> 사용자에게는 진짜 사유 대신
 * 엉뚱한 오류가 나가고, 남아야 할 감사 기록이 사라진다. 승인 여부가 미확정인 건을 기록하는 자리도
 * 같은 코드를 쓰므로, <b>돈이 걸린 상태를 못 적는 상황</b>까지 생긴다.
 *
 * <p>그래서 넣기 직전에 한 번 거른다. JSON 이면 그대로 두고, 아니면 문장을 감싼다.
 */
final class PaymentEventPayload {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PaymentEventPayload() {
    }

    /**
     * @param raw PG 응답 본문이거나, 사람이 읽는 실패 사유 문장
     * @return 그대로 저장해도 되는 JSON. 넣을 내용이 없으면 null
     */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;

        try {
            MAPPER.readTree(raw);
            return raw;
        } catch (Exception notJson) {
            // 문장이다. 이스케이프는 Jackson 에 맡긴다 — 따옴표나 줄바꿈이 섞여 있어도 안전하다
            try {
                ObjectNode wrapped = MAPPER.createObjectNode();
                wrapped.put("message", raw);
                return MAPPER.writeValueAsString(wrapped);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
