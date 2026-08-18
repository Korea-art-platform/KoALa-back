package com.koala.koalaback.infra.health;

import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("치명 의존성 — Redis 가 죽으면 트래픽을 받지 않는다")
@SpringBootTest(properties = "management.health.redis.enabled=false")
@Import(ReadinessCriticalTest.DeadRedis.class)
class ReadinessCriticalTest extends IntegrationTestSupport {
    @TestConfiguration
    static class DeadRedis {
        @Bean("redis")
        HealthIndicator redis() {
            return () -> Health.down()
                    .withDetail("error", "RedisConnectionFailureException: connection refused")
                    .build();
        }
    }

    @Autowired private HealthEndpoint healthEndpoint;

    @Test
    @DisplayName("Redis 가 죽으면 readiness 가 DOWN 이다")
    void redisFailureMakesReadinessDown() {
        assertThat(healthEndpoint.healthForPath("readiness").getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("그래도 liveness 는 UP 이다 — 여기서 DOWN 이 되면 재시작 루프에 빠진다")
    void redisFailureDoesNotKillLiveness() {
        assertThat(healthEndpoint.healthForPath("liveness").getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("DOWN 인 것은 redis 하나이고 DB 는 멀쩡하다")
    void onlyRedisIsDown() {
        var readiness = (CompositeHealthDescriptor) healthEndpoint.healthForPath("readiness");

        assertThat(readiness.getComponents().get("redis").getStatus()).isEqualTo(Status.DOWN);
        assertThat(readiness.getComponents().get("db").getStatus()).isEqualTo(Status.UP);
    }
}
