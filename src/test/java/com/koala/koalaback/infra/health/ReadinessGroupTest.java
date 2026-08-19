package com.koala.koalaback.infra.health;

import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("저하 의존성 — 메일이 죽어도 트래픽은 받는다")
@SpringBootTest(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1",
        "spring.mail.properties.mail.smtp.connectiontimeout=300",
        "spring.mail.properties.mail.smtp.timeout=300",
})
class ReadinessGroupTest extends IntegrationTestSupport {
    @Autowired private HealthEndpoint healthEndpoint;

    @Test
    @DisplayName("메일이 죽어도 readiness 는 UP — 이게 깨지면 SMTP 장애로 배포가 롤백된다")
    void mailFailureDoesNotAffectReadiness() {
        assertThat(mailStatus()).as("메일이 실제로 죽어 있어야 의미 있는 검사다").isEqualTo(Status.DOWN);

        assertThat(healthEndpoint.healthForPath("readiness").getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("같은 상황에서 종합 상태는 DOWN — 문제 자체는 드러나야 한다")
    void mailFailureIsStillVisibleInAggregate() {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("readiness 는 치명 의존성만 본다")
    void readinessWatchesOnlyCriticalDependencies() {
        assertThat(components("readiness"))
                .containsExactlyInAnyOrder("readinessState", "db", "diskSpace");
    }

    @Test
    @DisplayName("liveness 에는 외부 의존성이 없다 — 있으면 장애 때 재시작 루프에 빠진다")
    void livenessHasNoExternalDependency() {
        assertThat(components("liveness")).containsExactly("livenessState");
    }

    private Status mailStatus() {
        CompositeHealthDescriptor aggregate = (CompositeHealthDescriptor) healthEndpoint.health();
        return aggregate.getComponents().get("mail").getStatus();
    }

    private java.util.Set<String> components(String group) {
        return ((CompositeHealthDescriptor) healthEndpoint.healthForPath(group)).getComponents().keySet();
    }
}
