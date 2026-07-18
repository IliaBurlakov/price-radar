package com.priceradar.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public interface NotificationDeliveryProjection {

    UUID getOutboxId();

    String getNotificationType();

    int getAttemptCount();

    Instant getNextAttemptAt();

    boolean getActiveSubscription();

    long getTelegramChatId();

    long getExternalProductId();

    String getProductTitle();

    String getBrand();

    String getVariantDisplayName();

    String getCityName();

    String getCanonicalUrl();

    int getWalletDiscountPercent();

    Long getTargetPriceMinor();

    String getPayload();
}
