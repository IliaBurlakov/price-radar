package com.priceradar.feedback.infrastructure.persistence;

import com.priceradar.feedback.application.FeedbackStore;
import com.priceradar.feedback.domain.FeedbackMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class JdbcFeedbackStore implements FeedbackStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcFeedbackStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(FeedbackMessage feedback) {
        jdbcTemplate.update("""
                INSERT INTO feedback_messages (id, user_id, message, created_at)
                VALUES (?, ?, ?, ?)
                """, feedback.getId(), feedback.getUserId(), feedback.getMessage(),
                Timestamp.from(feedback.getCreatedAt()));
    }
}
