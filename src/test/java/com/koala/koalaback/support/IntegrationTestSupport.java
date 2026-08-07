package com.koala.koalaback.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 MySQL/Redis 가 필요한 통합 테스트의 공통 베이스.
 *
 * <p>컨테이너를 {@code static} 으로 두고 프로퍼티도 동일하게 주입하므로,
 * 이 클래스를 상속한 테스트들은 <b>같은 스프링 컨텍스트와 같은 컨테이너</b>를 재사용한다
 * (클래스마다 브로커/DB 를 새로 띄우면 테스트 시간이 몇 배가 된다).
 *
 * <p>스키마는 Flyway 가 아니라 엔티티에서 생성한다 — 운영 마이그레이션 체인은
 * V13/V14 의 FK 타입 문제로 빈 DB 에 재생되지 않기 때문이다.
 * 트랜잭션·락 검증에 필요한 것은 실제 InnoDB 이지 마이그레이션 산출물이 아니다.
 */
@SpringBootTest
public abstract class IntegrationTestSupport {

    // 싱글턴 컨테이너 패턴 — @Testcontainers/@Container 를 쓰지 않는다.
    // 그 조합은 컨테이너를 "선언한 클래스 단위"로 start/stop 하기 때문에,
    // 상속으로 공유하면 먼저 끝난 테스트 클래스가 컨테이너를 내려버려
    // 뒤에 도는 클래스가 죽은 DataSource 를 잡는다.
    // 직접 start() 하고 stop 하지 않으면 JVM 종료 시 Ryuk 이 정리한다.
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.flyway.locations", () -> "classpath:db/noop");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // 동시성 테스트가 스레드 수만큼 커넥션을 동시에 필요로 한다
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");

        // 통합 테스트에서 만료 스케줄러가 끼어들어 주문을 취소하면 검증이 흔들린다
        registry.add("order.expiry-scan-initial-delay-ms", () -> "3600000");
    }
}
