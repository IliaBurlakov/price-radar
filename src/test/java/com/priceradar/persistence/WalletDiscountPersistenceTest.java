package com.priceradar.persistence;

import com.priceradar.testsupport.PostgresTestContainer;
import com.priceradar.user.application.PendingWalletDiscountInput;
import com.priceradar.user.infrastructure.persistence.JdbcPendingWalletDiscountInputStore;
import org.flywaydb.core.Flyway;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class WalletDiscountPersistenceTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.create();

    @Test
    void persistsPendingInputAndEnforcesWalletDiscountRange() {
        String schema = "wallet_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        dataSource.setSchema(schema);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Instant now = Instant.parse("2026-08-31T08:00:00Z");
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_profiles (
                    id, telegram_user_id, telegram_chat_id, location_id,
                    wallet_discount_percent, created_at, updated_at, version
                ) VALUES (?, 7001, 7001, NULL, 3, ?, ?, 0)
                """, userId, Timestamp.from(now), Timestamp.from(now));
        JdbcPendingWalletDiscountInputStore store =
                new JdbcPendingWalletDiscountInputStore(jdbc);
        store.replace(new PendingWalletDiscountInput(
                userId, 7001L, 7001L, now, now.plusSeconds(900)
        ));

        assertThat(new JdbcPendingWalletDiscountInputStore(jdbc).find(7001L, 7001L))
                .get().satisfies(restored -> {
                    assertThat(restored.getUserId()).isEqualTo(userId);
                    assertThat(restored.getExpiresAt()).isEqualTo(now.plusSeconds(900));
                });
        jdbc.update("UPDATE user_profiles SET wallet_discount_percent = 20 WHERE id = ?", userId);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE user_profiles SET wallet_discount_percent = 21 WHERE id = ?", userId
        )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        store.remove(7001L, 7001L);
        assertThat(store.find(7001L, 7001L)).isEmpty();
    }
}
