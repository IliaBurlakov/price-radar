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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PriceInsightsMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.create();

    @Test
    void backfillsActiveDecreaseSubscriptionsFromValidSubscriptionHistory() {
        String schema = "price_insights_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flywayAtVersionFive = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("5"))
                .load();
        flywayAtVersionFive.migrate();

        JdbcTemplate jdbc = jdbcTemplate(schema);
        Instant createdAt = Instant.parse("2026-08-20T10:00:00Z");
        UUID watchTargetId = UUID.randomUUID();
        UUID subscriptionWithHistoryId = UUID.randomUUID();
        UUID subscriptionWithoutHistoryId = UUID.randomUUID();
        insertTrackingData(jdbc, watchTargetId, createdAt);
        insertSubscription(
                jdbc,
                subscriptionWithHistoryId,
                UUID.randomUUID(),
                1001L,
                watchTargetId,
                48_000L,
                createdAt.plusSeconds(300),
                createdAt
        );
        insertSubscription(
                jdbc,
                subscriptionWithoutHistoryId,
                UUID.randomUUID(),
                1002L,
                watchTargetId,
                50_000L,
                createdAt.plusSeconds(300),
                createdAt.plusSeconds(360)
        );

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();

        Map<String, Object> backfilled = jdbc.queryForMap(
                """
                        SELECT notification_reference_price_minor,
                               last_processed_price_observed_at
                        FROM subscriptions
                        WHERE id = ?
                        """,
                subscriptionWithHistoryId
        );
        assertThat(backfilled.get("notification_reference_price_minor")).isEqualTo(41_000L);
        assertThat(backfilled.get("last_processed_price_observed_at"))
                .isEqualTo(Timestamp.from(createdAt.plusSeconds(300)));

        Map<String, Object> preservedInitialState = jdbc.queryForMap(
                """
                        SELECT notification_reference_price_minor,
                               last_processed_price_observed_at
                        FROM subscriptions
                        WHERE id = ?
                        """,
                subscriptionWithoutHistoryId
        );
        assertThat(preservedInitialState.get("notification_reference_price_minor"))
                .isEqualTo(50_000L);
        assertThat(preservedInitialState.get("last_processed_price_observed_at"))
                .isEqualTo(Timestamp.from(createdAt.plusSeconds(300)));
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

    private void insertTrackingData(
            JdbcTemplate jdbc,
            UUID watchTargetId,
            Instant createdAt
    ) {
        UUID productId = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO products (
                            id, marketplace, external_product_id, canonical_url, created_at
                        ) VALUES (?, 'WILDBERRIES', 123456, ?, ?)
                        """,
                productId,
                "https://www.wildberries.ru/catalog/123456/detail.aspx",
                Timestamp.from(createdAt.minusSeconds(60))
        );
        jdbc.update(
                """
                        INSERT INTO watch_targets (
                            id, product_id, variant_kind, variant_value, dest, spp,
                            next_check_at, created_at, city_name
                        ) VALUES (?, ?, 'NO_VARIANT', 'NO_VARIANT', 1259570991, 30, ?, ?, 'Moscow')
                        """,
                watchTargetId,
                productId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt.minusSeconds(60))
        );

        insertRegularSnapshot(jdbc, watchTargetId, createdAt.minusSeconds(1), 30_000L);
        insertRegularSnapshot(jdbc, watchTargetId, createdAt.plusSeconds(60), 43_400L);
        insertNonRegularSnapshot(
                jdbc,
                watchTargetId,
                createdAt.plusSeconds(120),
                "BASIC_FALLBACK",
                "BASIC_FALLBACK",
                null,
                10_000L,
                true
        );
        insertNonRegularSnapshot(
                jdbc,
                watchTargetId,
                createdAt.plusSeconds(180),
                "UNAVAILABLE",
                null,
                null,
                null,
                false
        );
        insertNonRegularSnapshot(
                jdbc,
                watchTargetId,
                createdAt.plusSeconds(240),
                "NO_PRICE",
                null,
                null,
                null,
                true
        );
        insertRegularSnapshot(jdbc, watchTargetId, createdAt.plusSeconds(270), 41_000L);
        insertRegularSnapshot(jdbc, watchTargetId, createdAt.plusSeconds(300), 48_000L);
    }

    private void insertSubscription(
            JdbcTemplate jdbc,
            UUID subscriptionId,
            UUID userId,
            long telegramId,
            UUID watchTargetId,
            long baselinePriceMinor,
            Instant baselineObservedAt,
            Instant createdAt
    ) {
        jdbc.update(
                """
                        INSERT INTO user_profiles (
                            id, telegram_user_id, telegram_chat_id, city_name, dest, spp,
                            wallet_discount_percent, created_at, updated_at
                        ) VALUES (?, ?, ?, 'Moscow', 1259570991, 30, 3, ?, ?)
                        """,
                userId,
                telegramId,
                telegramId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
        jdbc.update(
                """
                        INSERT INTO subscriptions (
                            id, user_id, watch_target_id, notification_mode,
                            baseline_price_minor, baseline_observed_at,
                            threshold_state, status, created_at
                        ) VALUES (?, ?, ?, 'ANY_DECREASE', ?, ?, 'NOT_APPLICABLE', 'ACTIVE', ?)
                        """,
                subscriptionId,
                userId,
                watchTargetId,
                baselinePriceMinor,
                Timestamp.from(baselineObservedAt),
                Timestamp.from(createdAt)
        );
    }

    private void insertRegularSnapshot(
            JdbcTemplate jdbc,
            UUID watchTargetId,
            Instant observedAt,
            long priceMinor
    ) {
        insertNonRegularSnapshot(
                jdbc,
                watchTargetId,
                observedAt,
                "REGULAR_PRICE",
                "PRODUCT",
                priceMinor,
                null,
                true
        );
    }

    private void insertNonRegularSnapshot(
            JdbcTemplate jdbc,
            UUID watchTargetId,
            Instant observedAt,
            String status,
            String source,
            Long regularPriceMinor,
            Long marketingBasePriceMinor,
            boolean available
    ) {
        jdbc.update(
                """
                        INSERT INTO price_snapshots (
                            id, check_id, watch_target_id, observed_at, status, price_source,
                            regular_price_minor, marketing_base_price_minor, available
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                watchTargetId,
                Timestamp.from(observedAt),
                status,
                source,
                regularPriceMinor,
                marketingBasePriceMinor,
                available
        );
    }
}
