package com.priceradar.marketplace.application;

import com.priceradar.marketplace.domain.Marketplace;

import java.time.Instant;
import java.util.Optional;

public interface ProviderCooldownStore {

    Optional<Instant> findCooldownUntil(Marketplace marketplace);

    void saveCooldownUntil(Marketplace marketplace, Instant cooldownUntil, Instant updatedAt);
}
