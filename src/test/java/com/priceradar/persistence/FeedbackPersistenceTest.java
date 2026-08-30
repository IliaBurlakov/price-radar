package com.priceradar.persistence;

import com.priceradar.feedback.application.PendingFeedbackInput;
import com.priceradar.feedback.domain.FeedbackMessage;
import com.priceradar.feedback.infrastructure.persistence.JdbcFeedbackStore;
import com.priceradar.feedback.infrastructure.persistence.JdbcPendingFeedbackInputStore;
import com.priceradar.testsupport.PostgresTestContainer;
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
class FeedbackPersistenceTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.create();

    @Test
    void pendingStateAndFeedbackSurviveStoreRecreation() {
        String schema = "feedback_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setSchema(schema);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Instant now = Instant.parse("2026-08-28T03:00:00Z");
        UUID userId = insertUser(jdbc, now);
        PendingFeedbackInput pending = new PendingFeedbackInput(
                userId, 7001L, 7001L, now, now.plusSeconds(900));

        new JdbcPendingFeedbackInputStore(jdbc).replace(pending);
        PendingFeedbackInput replacement = new PendingFeedbackInput(
                userId, 7001L, 7001L, now.plusSeconds(10), now.plusSeconds(910));
        new JdbcPendingFeedbackInputStore(jdbc).replace(replacement);
        assertThat(new JdbcPendingFeedbackInputStore(jdbc).find(7001L, 7001L))
                .get().satisfies(restored -> {
                    assertThat(restored.getUserId()).isEqualTo(userId);
                    assertThat(restored.getCreatedAt()).isEqualTo(now.plusSeconds(10));
                });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pending_feedback_inputs",
                Long.class)).isEqualTo(1L);

        assertThatThrownBy(() -> new JdbcFeedbackStore(jdbc).save(new FeedbackMessage(
                UUID.randomUUID(), UUID.randomUUID(), "Чужой пользователь", now)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        FeedbackMessage feedback = new FeedbackMessage(UUID.randomUUID(), userId, "Работает хорошо", now);
        new JdbcFeedbackStore(jdbc).save(feedback);
        new JdbcPendingFeedbackInputStore(jdbc).remove(7001L, 7001L);

        assertThat(new JdbcPendingFeedbackInputStore(jdbc).find(7001L, 7001L)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT message FROM feedback_messages WHERE id = ?",
                String.class, feedback.getId())).isEqualTo("Работает хорошо");
    }

    private UUID insertUser(JdbcTemplate jdbc, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_profiles (
                    id, telegram_user_id, telegram_chat_id, location_id,
                    wallet_discount_percent, created_at, updated_at, version
                ) VALUES (?, 7001, 7001, NULL, 3, ?, ?, 0)
                """, id, Timestamp.from(now), Timestamp.from(now));
        return id;
    }
}
