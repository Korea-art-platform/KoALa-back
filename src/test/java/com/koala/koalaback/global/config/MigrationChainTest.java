package com.koala.koalaback.global.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이그레이션 체인이 <b>빈 DB 에서 처음부터 끝까지 실행되는지</b> 검증한다.
 *
 * <h3>왜 필요한가</h3>
 * <p>운영 DB 는 V14 에서 baseline 되어 V1~V14 가 실행된 적이 없다. 그래서 이 구간이
 * 깨져 있어도 아무도 모른 채 지나간다. 실제로 V13·V14 는 FK 타입이 맞지 않아
 * 빈 DB 에서는 실행조차 되지 않는 상태였고, 그 사실이 드러난 것은 V21 이 운영에서
 * 실패한 뒤였다.
 *
 * <p>이 테스트가 지키는 것은 두 가지다.
 * <ul>
 *   <li><b>재해 복구·스테이징 신설이 가능한가</b> — 빈 DB 로 스키마를 세울 수 있어야 한다.</li>
 *   <li><b>다음 마이그레이션을 쓸 때 앞 구간을 믿어도 되는가</b> — 체인이 실제로
 *       만들어내는 스키마가 곧 사실이어야 한다.</li>
 * </ul>
 *
 * <p>여기서 실패하면 "운영은 멀쩡하니 괜찮다"가 아니라, 다음 배포가 위험하다는 뜻이다.
 */
@DisplayName("마이그레이션 체인")
class MigrationChainTest {

    /** 운영에 실제로 적용된 마지막 버전 */
    private static final String LATEST_VERSION = "22";

    @Test
    @DisplayName("빈 DB 에 V1 부터 끝까지 순서대로 적용된다")
    void appliesCleanlyOnEmptyDatabase() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))) {
            mysql.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    // baseline 없이 처음부터 — 운영과 달리 건너뛰는 구간이 없어야 한다
                    .baselineOnMigrate(false)
                    .load();

            flyway.migrate();

            List<MigrationInfo> applied = List.of(flyway.info().applied());
            assertThat(applied)
                    .as("적용된 마이그레이션이 하나도 없으면 locations 설정이 잘못된 것")
                    .isNotEmpty();

            assertThat(applied)
                    .allSatisfy(m -> assertThat(m.getState().isFailed())
                            .as("%s 가 실패했다", m.getVersion())
                            .isFalse());

            assertThat(flyway.info().current().getVersion().toString())
                    .as("마지막 버전")
                    .isEqualTo(LATEST_VERSION);
        }
    }

    @Test
    @DisplayName("체인이 만든 스키마에 핵심 테이블이 모두 있다")
    void createsEveryCoreTable() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))) {
            mysql.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .load();
            flyway.migrate();

            List<String> tables = readTableNames(flyway.getConfiguration().getDataSource());

            // 주문·결제·재고처럼 돈과 직결된 것 + 최근 추가분
            assertThat(tables).contains(
                    "users", "admins", "artists", "skus", "sku_media", "sku_stock_ledger",
                    "carts", "cart_items", "orders", "order_items", "order_shipments",
                    "payments", "payment_events", "return_requests",
                    "notices", "inquiries", "sku_categories");
        }
    }

    private List<String> readTableNames(DataSource dataSource) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_schema = DATABASE()")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }
}
