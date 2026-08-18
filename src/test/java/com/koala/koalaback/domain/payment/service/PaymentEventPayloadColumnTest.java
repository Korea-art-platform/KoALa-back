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
