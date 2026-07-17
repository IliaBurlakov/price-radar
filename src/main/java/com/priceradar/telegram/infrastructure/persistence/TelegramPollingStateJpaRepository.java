package com.priceradar.telegram.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramPollingStateJpaRepository
        extends JpaRepository<TelegramPollingStateEntity, String> {
}
