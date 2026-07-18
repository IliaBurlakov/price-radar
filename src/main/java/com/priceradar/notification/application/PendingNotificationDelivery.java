package com.priceradar.notification.application;

import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.domain.RubleAmount;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class PendingNotificationDelivery {

    private final UUID outboxId;
    private final NotificationType type;
    private final int attemptCount;
    private final Instant nextAttemptAt;
    private final boolean activeSubscription;
    private final long chatId;
    private final long externalProductId;
    private final Optional<String> productTitle;
    private final Optional<String> brand;
    private final Optional<String> variantDisplayName;
    private final String cityName;
    private final String canonicalUrl;
    private final int walletDiscountPercent;
    private final Optional<RubleAmount> targetPrice;
    private final Optional<RubleAmount> previousPrice;
    private final RubleAmount currentPrice;
    private final Instant observedAt;

    public PendingNotificationDelivery(
            UUID outboxId,
            NotificationType type,
            int attemptCount,
            Instant nextAttemptAt,
            boolean activeSubscription,
            long chatId,
            long externalProductId,
            Optional<String> productTitle,
            Optional<String> brand,
            Optional<String> variantDisplayName,
            String cityName,
            String canonicalUrl,
            int walletDiscountPercent,
            Optional<RubleAmount> targetPrice,
            Optional<RubleAmount> previousPrice,
            RubleAmount currentPrice,
            Instant observedAt
    ) {
        if (outboxId == null || type == null || nextAttemptAt == null
                || productTitle == null || brand == null || variantDisplayName == null
                || targetPrice == null || previousPrice == null || currentPrice == null
                || observedAt == null) {
            throw new IllegalArgumentException("pending notification fields must not be null");
        }
        if (attemptCount < 0 || chatId <= 0 || externalProductId <= 0) {
            throw new IllegalArgumentException("pending notification numeric fields are invalid");
        }
        if (cityName == null || cityName.isBlank()
                || canonicalUrl == null || canonicalUrl.isBlank()) {
            throw new IllegalArgumentException("notification display fields must not be blank");
        }
        if (walletDiscountPercent < 0 || walletDiscountPercent > 100) {
            throw new IllegalArgumentException("walletDiscountPercent must be between 0 and 100");
        }
        validatePrices(type, targetPrice, previousPrice, currentPrice);
        this.outboxId = outboxId;
        this.type = type;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.activeSubscription = activeSubscription;
        this.chatId = chatId;
        this.externalProductId = externalProductId;
        this.productTitle = normalized(productTitle);
        this.brand = normalized(brand);
        this.variantDisplayName = normalized(variantDisplayName);
        this.cityName = cityName.trim();
        this.canonicalUrl = canonicalUrl.trim();
        this.walletDiscountPercent = walletDiscountPercent;
        this.targetPrice = targetPrice;
        this.previousPrice = previousPrice;
        this.currentPrice = currentPrice;
        this.observedAt = observedAt;
    }

    public UUID getOutboxId() {
        return outboxId;
    }

    public NotificationType getType() {
        return type;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public boolean hasActiveSubscription() {
        return activeSubscription;
    }

    public long getChatId() {
        return chatId;
    }

    public long getExternalProductId() {
        return externalProductId;
    }

    public Optional<String> getProductTitle() {
        return productTitle;
    }

    public Optional<String> getBrand() {
        return brand;
    }

    public Optional<String> getVariantDisplayName() {
        return variantDisplayName;
    }

    public String getCityName() {
        return cityName;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public int getWalletDiscountPercent() {
        return walletDiscountPercent;
    }

    public Optional<RubleAmount> getTargetPrice() {
        return targetPrice;
    }

    public Optional<RubleAmount> getPreviousPrice() {
        return previousPrice;
    }

    public RubleAmount getCurrentPrice() {
        return currentPrice;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    private void validatePrices(
            NotificationType type,
            Optional<RubleAmount> targetPrice,
            Optional<RubleAmount> previousPrice,
            RubleAmount currentPrice
    ) {
        if (currentPrice.getMinorUnits() == 0) {
            throw new IllegalArgumentException("currentPrice must be positive");
        }
        if (type == NotificationType.PRICE_DECREASE) {
            RubleAmount previous = previousPrice.orElseThrow(() ->
                    new IllegalArgumentException("PRICE_DECREASE requires previousPrice"));
            if (previous.getMinorUnits() <= currentPrice.getMinorUnits()) {
                throw new IllegalArgumentException("PRICE_DECREASE requires a lower currentPrice");
            }
            return;
        }
        if (targetPrice.isEmpty()) {
            throw new IllegalArgumentException("TARGET_REACHED requires targetPrice");
        }
        if (previousPrice.isPresent()) {
            throw new IllegalArgumentException("TARGET_REACHED must not have previousPrice");
        }
    }

    private Optional<String> normalized(Optional<String> value) {
        return value.map(String::trim).filter(item -> !item.isBlank());
    }
}
