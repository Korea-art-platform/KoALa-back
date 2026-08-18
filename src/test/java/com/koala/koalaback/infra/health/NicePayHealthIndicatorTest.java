package com.koala.koalaback.infra.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("나이스 상태 표시 — 참고 등급")
class NicePayHealthIndicatorTest {
    @Test
    @DisplayName("결제사에 닿지 않아도 DOWN 은 아니다 — 외부 장애로 내 인스턴스가 빠지면 안 된다")
    void unreachableNeverReportsDown() {
        Health health = new NicePayHealthIndicator("https://unreachable.invalid/v1").health();

        assertThat(health.getStatus()).isNotEqualTo(Status.DOWN);
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("reachable", false);
    }

    @Test
    @DisplayName("닿지 않아도 정해진 시간 안에 끝난다 — 헬스체크가 느려지면 그게 장애다")
    void probeIsBounded() {
        Instant start = Instant.now();

        new NicePayHealthIndicator("https://10.255.255.1/v1").health();

        Duration elapsed = Duration.between(start, Instant.now());
        assertThat(elapsed).isLessThan(NicePayHealthIndicator.PROBE_TIMEOUT.plusSeconds(2));
    }

    @Test
    @DisplayName("샌드박스인지 운영인지 표시한다 — 운영 키로 바꿨는지 눈으로 확인할 수 있어야 한다")
    void reportsWhichEnvironmentItTalksTo() {
        Health sandbox = new NicePayHealthIndicator("https://sandbox-api.nicepay.co.kr/v1").health();
        Health production = new NicePayHealthIndicator("https://api.nicepay.co.kr/v1").health();

        assertThat(sandbox.getDetails()).containsEntry("mode", "sandbox");
        assertThat(production.getDetails()).containsEntry("mode", "production");
    }

    @Test
    @DisplayName("주소에서 호스트만 뽑아 쓴다")
    void extractsHost() {
        assertThat(NicePayHealthIndicator.hostOf("https://sandbox-api.nicepay.co.kr/v1"))
                .isEqualTo("sandbox-api.nicepay.co.kr");
        assertThat(NicePayHealthIndicator.hostOf("망가진주소")).isEqualTo("망가진주소");
    }
}
