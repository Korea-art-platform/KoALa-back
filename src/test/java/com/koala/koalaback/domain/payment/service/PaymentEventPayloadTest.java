package com.koala.koalaback.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 결제 이벤트 기록에 넣을 값 다듬기.
 *
 * <p>{@code payload_json} 은 MySQL JSON 칼럼이다. 여기 평문이 들어가면 저장이 거부되고,
 * 기록하려던 트랜잭션이 통째로 뒤집힌다. 즉 <b>PG 가 거절했다는 사실 자체를 못 남긴다.</b>
 * 실제로 환불이 거절됐을 때 사용자에게 진짜 사유 대신 엉뚱한 오류가 나갔다.
 */
@DisplayName("결제 이벤트 payload 다듬기")
class PaymentEventPayloadTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("PG 응답 본문은 그대로 둔다")
    void jsonPassesThrough() {
        String raw = "{\"resultCode\":\"0000\",\"amount\":150000}";

        assertThat(PaymentEventPayload.normalize(raw)).isEqualTo(raw);
    }

    @Test
    @DisplayName("사람이 읽는 문장은 JSON 으로 감싼다")
    void plainTextIsWrapped() throws Exception {
        String message = "부분취소는 운영 환경에서 이용 가능(샌드박스는 부분취소 미제공)";

        String result = PaymentEventPayload.normalize(message);

        assertThat(mapper.readTree(result).path("message").asText()).isEqualTo(message);
    }

    @Test
    @DisplayName("따옴표와 줄바꿈이 섞여 있어도 깨지지 않는다")
    void escapesSpecialCharacters() throws Exception {
        String message = "PG 응답: \"실패\"\n사유: timeout\t재시도 필요";

        String result = PaymentEventPayload.normalize(message);

        assertThatCode(() -> mapper.readTree(result)).doesNotThrowAnyException();
        assertThat(mapper.readTree(result).path("message").asText()).isEqualTo(message);
    }

    @Test
    @DisplayName("넣을 내용이 없으면 null — 빈 문자열은 JSON 이 아니라 저장이 거부된다")
    void blankBecomesNull() {
        assertThat(PaymentEventPayload.normalize(null)).isNull();
        assertThat(PaymentEventPayload.normalize("")).isNull();
        assertThat(PaymentEventPayload.normalize("   ")).isNull();
    }

    @Test
    @DisplayName("어떤 값을 넣어도 결과는 항상 JSON 이거나 null 이다")
    void resultIsAlwaysValidJsonOrNull() {
        String[] inputs = {
                "[1,2,3]",
                "{\"a\":1}",
                "그냥 문장",
                "1234",
                "{깨진 JSON",
                "null",
                "한글 · 특수문자 ★ 이모지 🐨",
        };

        for (String input : inputs) {
            String result = PaymentEventPayload.normalize(input);
            if (result == null) continue;
            assertThatCode(() -> mapper.readTree(result))
                    .as("입력: %s", input)
                    .doesNotThrowAnyException();
        }
    }
}
