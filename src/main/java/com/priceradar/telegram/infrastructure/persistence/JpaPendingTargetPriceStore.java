package com.priceradar.telegram.infrastructure.persistence;

import com.priceradar.telegram.application.PendingTargetPrice;
import com.priceradar.telegram.application.PendingTargetPriceStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaPendingTargetPriceStore implements PendingTargetPriceStore {

    private final JdbcTemplate jdbcTemplate;

    public JpaPendingTargetPriceStore(JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void put(PendingTargetPrice pending, Instant now) {
        if (pending == null || now == null) {
            throw new IllegalArgumentException("pending target price fields must not be null");
        }
        jdbcTemplate.update(
                "DELETE FROM telegram_pending_target_prices WHERE expires_at < ?",
                Timestamp.from(now)
        );
        jdbcTemplate.update("""
                INSERT INTO telegram_pending_target_prices (
                    telegram_user_id, chat_id, purpose, quote_snapshot_id,
                    subscription_id, expires_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (telegram_user_id) DO UPDATE
                SET chat_id = EXCLUDED.chat_id,
                    purpose = EXCLUDED.purpose,
                    quote_snapshot_id = EXCLUDED.quote_snapshot_id,
                    subscription_id = EXCLUDED.subscription_id,
                    expires_at = EXCLUDED.expires_at,
                    created_at = EXCLUDED.created_at
                """,
                pending.getTelegramUserId(),
                pending.getChatId(),
                pending.getPurpose().name(),
                pending.getPurpose() == PendingTargetPrice.Purpose.CREATE_SUBSCRIPTION
                        ? pending.getReferenceId() : null,
                pending.getPurpose() == PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION
                        ? pending.getReferenceId() : null,
                Timestamp.from(pending.getExpiresAt()),
                Timestamp.from(now)
        );
    }

    @Override
    @Transactional
    public Optional<PendingTargetPrice> find(
            long telegramUserId,
            long chatId,
            Instant now
    ) {
        validateIdentity(telegramUserId, chatId, now);
        List<PendingTargetPrice> found = jdbcTemplate.query("""
                SELECT telegram_user_id, chat_id, purpose, quote_snapshot_id,
                       subscription_id, expires_at
                FROM telegram_pending_target_prices
                WHERE telegram_user_id = ?
                """, this::map, telegramUserId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PendingTargetPrice pending = found.getFirst();
        if (pending.getChatId() != chatId) {
            remove(pending);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    @Override
    @Transactional
    public void remove(PendingTargetPrice pending) {
        if (pending == null) {
            throw new IllegalArgumentException("pending target price must not be null");
        }
        jdbcTemplate.update(
                """
                        DELETE FROM telegram_pending_target_prices
                        WHERE telegram_user_id = ?
                          AND chat_id = ?
                          AND purpose = ?
                          AND COALESCE(quote_snapshot_id, subscription_id) = ?
                          AND expires_at = ?
                        """,
                pending.getTelegramUserId(),
                pending.getChatId(),
                pending.getPurpose().name(),
                pending.getReferenceId(),
                Timestamp.from(pending.getExpiresAt())
        );
    }

    @Override
    @Transactional
    public void remove(long telegramUserId, long chatId) {
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        jdbcTemplate.update(
                "DELETE FROM telegram_pending_target_prices WHERE telegram_user_id = ? AND chat_id = ?",
                telegramUserId,
                chatId
        );
    }

    private PendingTargetPrice map(ResultSet resultSet, int rowNumber) throws SQLException {
        PendingTargetPrice.Purpose purpose = PendingTargetPrice.Purpose.valueOf(
                resultSet.getString("purpose")
        );
        String referenceColumn = purpose == PendingTargetPrice.Purpose.CREATE_SUBSCRIPTION
                ? "quote_snapshot_id"
                : "subscription_id";
        return new PendingTargetPrice(
                resultSet.getLong("telegram_user_id"),
                resultSet.getLong("chat_id"),
                purpose,
                resultSet.getObject(referenceColumn, UUID.class),
                resultSet.getTimestamp("expires_at").toInstant()
        );
    }

    private void validateIdentity(long telegramUserId, long chatId, Instant now) {
        if (telegramUserId <= 0 || chatId <= 0 || now == null) {
            throw new IllegalArgumentException("pending target lookup fields are invalid");
        }
    }
}
