package com.priceradar.marketplace.infrastructure.persistence;

import com.priceradar.marketplace.domain.Marketplace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "provider_states")
public class ProviderStateEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", length = 32)
    private Marketplace marketplace;

    @Column(name = "cooldown_until")
    private Instant cooldownUntil;

    @Column(name = "last_request_at")
    private Instant lastRequestAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProviderStateEntity() {
    }

    public ProviderStateEntity(Marketplace marketplace, Instant updatedAt) {
        this.marketplace = marketplace;
        this.updatedAt = updatedAt;
    }

    public void activateCooldown(Instant newCooldownUntil, Instant updateTime) {
        if (cooldownUntil == null || newCooldownUntil.isAfter(cooldownUntil))
            cooldownUntil = newCooldownUntil;
        updatedAt = updateTime;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public Instant getCooldownUntil() {
        return cooldownUntil;
    }

    public Instant getLastRequestAt() {
        return lastRequestAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
