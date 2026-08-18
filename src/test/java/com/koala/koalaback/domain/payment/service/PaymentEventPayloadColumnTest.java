package com.koala.koalaback.domain.payment.service;

import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code payment_events.payload_json} 이 왜 다듬어야 하는 칼럼인지 실제 DB 로 확인한다.
 *
 * <p>{@link PaymentEventPayloadTest} 는 다듬는 규칙만 본다. 그 규칙이 <b>왜 필요한지</b>는
 * DB 가 정한다 — 칼럼 타입이 JSON 이라 평문을 거부한다. 누군가 "그냥 문자열 칼럼으로 바꾸면
 * 되지 않나" 하고 타입을 바꾸면 이 테스트가 먼저 알려 준다.
 */
@DisplayName("결제 이벤트 payload 칼럼")
class PaymentEventPayloadColumnTest extends IntegrationTestSupport {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("payload_json 은 JSON 칼럼이다")
    void columnIsJsonType() {
        String type = jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'payment_events'
                   AND COLUMN_NAME = 'payload_json'
                """, String.class);

        assertThat(type).isEqualTo("json");
    }

    @Test
    @DisplayName("평문은 저장이 거부된다 — 다듬지 않으면 실패 사유를 기록할 수 없다")
    void plainTextIsRejectedByDatabase() {
        jdbcTemplate.execute("CREATE TEMPORARY TABLE payload_probe (p JSON)");

        String message = "부분취소는 운영 환경에서 이용 가능(샌드박스는 부분취소 미제공)";

        assertThatThrownBy(() ->
                jdbcTemplate.update("INSERT INTO payload_probe VALUES (?)", message))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다듬은 값은 저장된다")
    void normalizedTextIsAccepted() {
        jdbcTemplate.execute("CREATE TEMPORARY TABLE payload_probe2 (p JSON)");

        String message = "부분취소는 운영 환경에서 이용 가능(샌드박스는 부분취소 미제공)";

        assertThatCode(() -> jdbcTemplate.update(
                "INSERT INTO payload_probe2 VALUES (?)", PaymentEventPayload.normalize(message)))
                .doesNotThrowAnyException();

        String stored = jdbcTemplate.queryForObject(
                "SELECT p ->> '$.message' FROM payload_probe2", String.class);
        assertThat(stored).as("사유가 그대로 읽혀야 한다").isEqualTo(message);
    }
}
