package com.priceradar.persistence;

import com.priceradar.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RegionSelectionMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.create();

    @Test
    void keepsExistingUsersConfiguredAndRequiresSelectionForNewUsers() {
        String schema = "region_selection_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("14"))
                .load()
                .migrate();
        JdbcTemplate jdbc = jdbcTemplate(schema);
        insertUser(jdbc, 7001L);

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();

        assertThat(jdbc.queryForObject(
                "SELECT location_id FROM user_profiles WHERE telegram_user_id = 7001",
                UUID.class
        )).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000101"));

        insertUnconfiguredUser(jdbc, 7002L);
        assertThat(jdbc.queryForObject(
                "SELECT location_id FROM user_profiles WHERE telegram_user_id = 7002",
                UUID.class
        )).isNull();
    }

    private void insertUser(JdbcTemplate jdbc, long telegramId) {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        jdbc.update(
                """
                        INSERT INTO user_profiles (
                            id, telegram_user_id, telegram_chat_id, region_code,
                            region_selected, wallet_discount_percent, created_at, updated_at, version
                        ) VALUES (?, ?, ?, 'MOSCOW', TRUE, 3, ?, ?, 0)
                        """,
                UUID.randomUUID(),
                telegramId,
                telegramId,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private void insertUnconfiguredUser(JdbcTemplate jdbc, long telegramId) {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        jdbc.update(
                """
                        INSERT INTO user_profiles (
                            id, telegram_user_id, telegram_chat_id, location_id,
                            wallet_discount_percent, created_at, updated_at, version
                        ) VALUES (?, ?, ?, NULL, 3, ?, ?, 0)
                        """,
                UUID.randomUUID(), telegramId, telegramId,
                Timestamp.from(now), Timestamp.from(now)
        );
    }

    private JdbcTemplate jdbcTemplate(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        return new JdbcTemplate(dataSource);
    }
}
