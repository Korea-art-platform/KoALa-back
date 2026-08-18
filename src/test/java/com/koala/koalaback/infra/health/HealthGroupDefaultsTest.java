package com.koala.koalaback.infra.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("health group 기본값 — 설정 파일이 없어도 jar 안에서 따라간다")
class HealthGroupDefaultsTest {
    private static final String READINESS_KEY = "management.endpoint.health.group.readiness.include";
    private static final String LIVENESS_KEY = "management.endpoint.health.group.liveness.include";

    private StandardEnvironment environmentWith(Map<String, Object> existing) {
        StandardEnvironment environment = new StandardEnvironment();
        if (!existing.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("given", existing));
        }
        new HealthGroupDefaults(null).postProcessEnvironment(environment, null);
        return environment;
    }

    @Test
    @DisplayName("설정이 없으면 기본 그룹을 채운다")
    void fillsGroupsWhenNothingConfigured() {
        var environment = environmentWith(Map.of());

        assertThat(environment.getProperty(READINESS_KEY))
                .isEqualTo("readinessState, db, redis, diskSpace");
        assertThat(environment.getProperty(LIVENESS_KEY)).isEqualTo("livenessState");
    }

    @Test
    @DisplayName("서버 설정이 있으면 그쪽이 이긴다 — 기본값이 운영 설정을 덮으면 안 된다")
    void serverConfigurationWins() {
        var environment = environmentWith(Map.of(READINESS_KEY, "readinessState, db"));

        assertThat(environment.getProperty(READINESS_KEY)).isEqualTo("readinessState, db");
    }

    @Test
    @DisplayName("두 번 실행돼도 property source 가 쌓이지 않는다")
    void isIdempotent() {
        StandardEnvironment environment = new StandardEnvironment();
        new HealthGroupDefaults(null).postProcessEnvironment(environment, null);
        new HealthGroupDefaults(null).postProcessEnvironment(environment, null);

        long added = environment.getPropertySources().stream()
                .filter(source -> "koalaHealthGroupDefaults".equals(source.getName()))
                .count();
        assertThat(added).isEqualTo(1);
    }

    @Test
    @DisplayName("liveness 기본값에 외부 의존성이 없다")
    void livenessDefaultHasNoExternalDependency() {
        assertThat(HealthGroupDefaults.LIVENESS).isEqualTo("livenessState");
    }

    @Test
    @DisplayName("readiness 기본값에 결제사와 메일이 없다")
    void readinessDefaultExcludesExternalSystems() {
        assertThat(HealthGroupDefaults.READINESS)
                .doesNotContain("nicepay", "payple", "toss", "mail");
    }
}
