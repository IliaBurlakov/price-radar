package com.priceradar.marketplace.infrastructure.persistence;

import com.priceradar.marketplace.application.ProviderCooldownStore;
import com.priceradar.marketplace.domain.Marketplace;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaProviderCooldownStore implements ProviderCooldownStore {

    private final ProviderStateJpaRepository repository;

    public JpaProviderCooldownStore(ProviderStateJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findCooldownUntil(Marketplace marketplace) {
        return repository.findById(marketplace)
                .map(ProviderStateEntity::getCooldownUntil);
    }

    @Override
    @Transactional
    public void saveCooldownUntil(
            Marketplace marketplace,
            Instant cooldownUntil,
            Instant updatedAt
    ) {
        ProviderStateEntity state = repository.findById(marketplace)
                .orElseGet(() -> new ProviderStateEntity(marketplace, updatedAt));
        state.activateCooldown(cooldownUntil, updatedAt);
        repository.save(state);
    }
}
