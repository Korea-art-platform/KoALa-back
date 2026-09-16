package com.koala.koalaback.global.config;

import org.flywaydb.core.Flyway;
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

@DisplayName("V35 — 주문자 찾기용 해시 필수화")
class OrdererIndexMigrationTest {

    @Test
    @DisplayName("주문이 있고 해시가 모두 채워졌으면 NOT NULL 이 걸린다 — 운영에서 타는 경로")
    void appliesWhenEveryOrderIsIndexed() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))) {
            mysql.start();
            DataSource ds = migrateTo(mysql, "34");

            insertOrder(ds, "ORD-A", "'hash-email'", "'hash-phone'");
            migrateTo(mysql, null);

            assertThat(nullable(ds, "orderer_email_hash")).isEqualTo("NO");
            assertThat(nullable(ds, "orderer_phone_hash")).isEqualTo("NO");
            assertThat(nullable(ds, "orderer_phone_last4")).isEqualTo("YES");
        }
    }

    @Test
    @DisplayName("해시가 빠진 주문이 있으면 건너뛴다 — 키 없는 로컬 DB")
    void skipsWhenSomeOrderIsNotIndexed() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))) {
            mysql.start();
            DataSource ds = migrateTo(mysql, "34");

            insertOrder(ds, "ORD-B", "NULL", "NULL");
            migrateTo(mysql, null);

            assertThat(nullable(ds, "orderer_email_hash")).isEqualTo("YES");
        }
    }

    @Test
    @DisplayName("주문이 없으면 건너뛴다 — 새로 만든 DB")
    void skipsOnEmptyTable() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))) {
            mysql.start();
            DataSource ds = migrateTo(mysql, null);

            assertThat(nullable(ds, "orderer_email_hash")).isEqualTo("YES");
        }
    }

    private DataSource migrateTo(MySQLContainer<?> mysql, String target) {
        var config = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false);
        if (target != null) config.target(target);
        Flyway flyway = config.load();
        flyway.migrate();
        return flyway.getConfiguration().getDataSource();
    }

    private void insertOrder(DataSource ds, String orderNo, String emailHash, String phoneHash) throws Exception {
        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();

        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT column_name, data_type, column_type
                     FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'orders'
                       AND is_nullable = 'NO' AND column_default IS NULL
                       AND extra NOT LIKE '%auto_increment%'
                     """)) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name.equals("order_no")) continue;
                columns.add(name);
                values.add(dummyValue(rs.getString(2), rs.getString(3)));
            }
        }

        columns.add("order_no");
        values.add("'" + orderNo + "'");
        columns.add("orderer_email_hash");
        values.add(emailHash);
        columns.add("orderer_phone_hash");
        values.add(phoneHash);

        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO orders (" + String.join(", ", columns) + ") VALUES ("
                    + String.join(", ", values) + ")");
        }
    }

    private String dummyValue(String dataType, String columnType) {
        return switch (dataType) {
            case "varchar", "char", "text", "longtext", "mediumtext", "tinytext" -> "'x'";
            case "datetime", "timestamp" -> "NOW()";
            case "date" -> "CURDATE()";
            case "enum" -> columnType.substring(columnType.indexOf('\''), columnType.indexOf('\'', columnType.indexOf('\'') + 1) + 1);
            case "json" -> "'{}'";
            default -> "0";
        };
    }

    private String nullable(DataSource ds, String column) throws Exception {
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT is_nullable FROM information_schema.columns "
                             + "WHERE table_schema = DATABASE() AND table_name = 'orders' "
                             + "AND column_name = '" + column + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
