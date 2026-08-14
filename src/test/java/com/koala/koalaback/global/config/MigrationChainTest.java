package com.koala.koalaback.global.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("마이그레이션 체인")
class MigrationChainTest {
    private static String expectedLatestVersion() {
        Pattern versionPattern = Pattern.compile("^V(\\d+)__");
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            return files.map(p -> p.getFileName().toString())
                    .map(versionPattern::matcher)
                    .filter(Matcher::find)
                    .map(m -> Integer.parseInt(m.group(1)))
                    .max(Integer::compareTo)
                    .map(String::valueOf)
                    .orElseThrow(() -> new IllegalStateException("마이그레이션 파일을 찾지 못했다"));
        } catch (IOException e) {
            throw new IllegalStateException("마이그레이션 디렉터리를 읽지 못했다", e);
        }
    }

    @Test
    @DisplayName("빈 DB 에 V1 부터 끝까지 순서대로 적용된다")
    void appliesCleanlyOnEmptyDatabase() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))) {
            mysql.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")

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
                    .as("마지막 버전 — 파일에 있는 최신 마이그레이션까지 적용됐는가")
                    .isEqualTo(expectedLatestVersion());
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

            assertThat(tables).contains(
                    "users", "admins", "artists", "skus", "sku_media", "sku_stock_ledger",
                    "carts", "cart_items", "orders", "order_items", "order_shipments",
                    "payments", "payment_events", "return_requests",
                    "notices", "inquiries", "sku_categories", "artist_settlements");
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
