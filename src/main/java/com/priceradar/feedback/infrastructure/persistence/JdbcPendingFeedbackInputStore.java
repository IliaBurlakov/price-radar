package com.priceradar.feedback.infrastructure.persistence;

import com.priceradar.feedback.application.PendingFeedbackInput;
import com.priceradar.feedback.application.PendingFeedbackInputStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPendingFeedbackInputStore implements PendingFeedbackInputStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPendingFeedbackInputStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void replace(PendingFeedbackInput pending) {
        jdbcTemplate.update("""
                INSERT INTO pending_feedback_inputs (
                    user_id, telegram_user_id, chat_id, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    telegram_user_id = EXCLUDED.telegram_user_id,
                    chat_id = EXCLUDED.chat_id,
                    created_at = EXCLUDED.created_at,
                    expires_at = EXCLUDED.expires_at
                """, pending.getUserId(), pending.getTelegramUserId(), pending.getChatId(),
                Timestamp.from(pending.getCreatedAt()), Timestamp.from(pending.getExpiresAt()));
    }

    @Override
    public Optional<PendingFeedbackInput> find(long telegramUserId, long chatId) {
        List<PendingFeedbackInput> found = jdbcTemplate.query("""
                SELECT user_id, telegram_user_id, chat_id, created_at, expires_at
                FROM pending_feedback_inputs
                WHERE telegram_user_id = ? AND chat_id = ?
                FOR UPDATE
                """, this::map, telegramUserId, chatId);
        return found.stream().findFirst();
    }

    @Override
    public void remove(long telegramUserId, long chatId) {
        jdbcTemplate.update("""
                DELETE FROM pending_feedback_inputs
                WHERE telegram_user_id = ? AND chat_id = ?
                """, telegramUserId, chatId);
    }

    private PendingFeedbackInput map(ResultSet rs, int rowNumber) throws SQLException {
        return new PendingFeedbackInput(
                rs.getObject("user_id", java.util.UUID.class),
                rs.getLong("telegram_user_id"), rs.getLong("chat_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()
        );
    }
}
