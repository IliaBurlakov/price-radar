package com.priceradar.user.infrastructure.persistence;

import com.priceradar.user.application.PendingWalletDiscountInput;
import com.priceradar.user.application.PendingWalletDiscountInputStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPendingWalletDiscountInputStore implements PendingWalletDiscountInputStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPendingWalletDiscountInputStore(JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void replace(PendingWalletDiscountInput pending) {
        if (pending == null) {
            throw new IllegalArgumentException("pending wallet discount must not be null");
        }
        jdbcTemplate.update("""
                INSERT INTO pending_wallet_discount_inputs (
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
    public Optional<PendingWalletDiscountInput> find(long telegramUserId, long chatId) {
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        List<PendingWalletDiscountInput> found = jdbcTemplate.query("""
                SELECT user_id, telegram_user_id, chat_id, created_at, expires_at
                FROM pending_wallet_discount_inputs
                WHERE telegram_user_id = ? AND chat_id = ?
                FOR UPDATE
                """, this::map, telegramUserId, chatId);
        return found.stream().findFirst();
    }

    @Override
    public void remove(long telegramUserId, long chatId) {
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        jdbcTemplate.update("""
                DELETE FROM pending_wallet_discount_inputs
                WHERE telegram_user_id = ? AND chat_id = ?
                """, telegramUserId, chatId);
    }

    private PendingWalletDiscountInput map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PendingWalletDiscountInput(
                resultSet.getObject("user_id", java.util.UUID.class),
                resultSet.getLong("telegram_user_id"),
                resultSet.getLong("chat_id"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant()
        );
    }
}
