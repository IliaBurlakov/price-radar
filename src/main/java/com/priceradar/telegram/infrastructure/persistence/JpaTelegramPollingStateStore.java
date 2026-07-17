package com.priceradar.telegram.infrastructure.persistence;

import com.priceradar.telegram.application.TelegramPollingStateStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.OptionalLong;

@Repository
public class JpaTelegramPollingStateStore implements TelegramPollingStateStore {

    private final TelegramPollingStateJpaRepository stateRepository;

    public JpaTelegramPollingStateStore(TelegramPollingStateJpaRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OptionalLong findLastConfirmedUpdateId(String botKey) {
        return stateRepository.findById(botKey)
                .map(TelegramPollingStateEntity::getLastConfirmedUpdateId)
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    @Override
    @Transactional
    public void confirm(String botKey, long updateId) {
        if (botKey == null || botKey.isBlank()) {
            throw new IllegalArgumentException("botKey must not be blank");
        }
        if (updateId < 0) {
            throw new IllegalArgumentException("updateId must be non-negative");
        }

        Instant now = Instant.now();
        TelegramPollingStateEntity state = stateRepository.findById(botKey)
                .orElseGet(() -> new TelegramPollingStateEntity(botKey, null, now));
        if (state.confirm(updateId, now)) {
            stateRepository.save(state);
        }
    }
}
