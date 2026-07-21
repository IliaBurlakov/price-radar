package com.priceradar.notification.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.notification.application.NotificationDeliveryStore;
import com.priceradar.notification.application.PendingNotificationDelivery;
import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.domain.RubleAmount;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaNotificationDeliveryStore implements NotificationDeliveryStore {

    private final NotificationOutboxJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaNotificationDeliveryStore(
            NotificationOutboxJpaRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingNotificationDelivery> findDue(Instant now, int limit) {
        if (now == null || limit <= 0) {
            throw new IllegalArgumentException("delivery query fields are invalid");
        }
        return repository.findDueDeliveries(now, limit).stream()
                .map(this::toDelivery)
                .toList();
    }

    @Override
    @Transactional
    public boolean claim(
            UUID outboxId,
            Instant expectedNextAttemptAt,
            Instant now,
            Instant claimUntil
    ) {
        validateClaim(outboxId, expectedNextAttemptAt, now, claimUntil);
        return repository.claimForDelivery(
                outboxId,
                expectedNextAttemptAt,
                now,
                claimUntil
        ) == 1;
    }

    @Override
    @Transactional
    public boolean markSent(UUID outboxId, Instant claimUntil, Instant sentAt) {
        if (outboxId == null || claimUntil == null || sentAt == null) {
            throw new IllegalArgumentException("sent delivery fields must not be null");
        }
        return repository.markSent(outboxId, claimUntil, sentAt) == 1;
    }

    @Override
    @Transactional
    public boolean markRetry(
            UUID outboxId,
            Instant claimUntil,
            int attemptCount,
            Instant nextAttemptAt,
            String errorCode
    ) {
        validateFailure(outboxId, claimUntil, attemptCount, errorCode);
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("nextAttemptAt must be set for retry");
        }
        return repository.markRetry(
                outboxId,
                claimUntil,
                attemptCount,
                nextAttemptAt,
                errorCode
        ) == 1;
    }

    @Override
    @Transactional
    public boolean markFailed(
            UUID outboxId,
            Instant claimUntil,
            int attemptCount,
            String errorCode
    ) {
        validateFailure(outboxId, claimUntil, attemptCount, errorCode);
        return repository.markFailed(
                outboxId,
                claimUntil,
                attemptCount,
                errorCode
        ) == 1;
    }

    private PendingNotificationDelivery toDelivery(NotificationDeliveryProjection projection) {
        JsonNode payload = readPayload(projection.getPayload());
        long currentPriceMinor = requiredPositiveLong(payload, "currentPriceMinor");
        Optional<RubleAmount> previousPrice = optionalPositiveLong(payload, "previousPriceMinor")
                .map(RubleAmount::ofMinorUnits);
        Instant observedAt = requiredInstant(payload, "observedAt");
        return new PendingNotificationDelivery(
                projection.getOutboxId(),
                NotificationType.valueOf(projection.getNotificationType()),
                projection.getAttemptCount(),
                projection.getNextAttemptAt(),
                projection.getActiveSubscription(),
                projection.getTelegramChatId(),
                projection.getExternalProductId(),
                Optional.ofNullable(projection.getProductTitle()),
                Optional.ofNullable(projection.getBrand()),
                Optional.ofNullable(projection.getVariantDisplayName()),
                projection.getCityName(),
                projection.getCanonicalUrl(),
                projection.getWalletDiscountPercent(),
                Optional.ofNullable(projection.getTargetPriceMinor())
                        .map(RubleAmount::ofMinorUnits),
                previousPrice,
                RubleAmount.ofMinorUnits(currentPriceMinor),
                observedAt
        );
    }

    private JsonNode readPayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Notification payload is malformed", exception);
        }
    }

    private long requiredPositiveLong(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (!value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalStateException("Notification payload has invalid " + fieldName);
        }
        return value.longValue();
    }

    private Optional<Long> optionalPositiveLong(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (!value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalStateException("Notification payload has invalid " + fieldName);
        }
        return Optional.of(value.longValue());
    }

    private Instant requiredInstant(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (!value.isTextual()) {
            throw new IllegalStateException("Notification payload has invalid " + fieldName);
        }
        try {
            return Instant.parse(value.textValue());
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("Notification payload has invalid " + fieldName, exception);
        }
    }

    private void validateClaim(
            UUID outboxId,
            Instant expectedNextAttemptAt,
            Instant now,
            Instant claimUntil
    ) {
        if (outboxId == null || expectedNextAttemptAt == null || now == null || claimUntil == null) {
            throw new IllegalArgumentException("claim fields must not be null");
        }
        if (!claimUntil.isAfter(now)) {
            throw new IllegalArgumentException("claimUntil must be after now");
        }
    }

    private void validateFailure(
            UUID outboxId,
            Instant claimUntil,
            int attemptCount,
            String errorCode
    ) {
        if (outboxId == null || claimUntil == null || errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("delivery failure fields must not be null or blank");
        }
        if (attemptCount <= 0 || errorCode.length() > 64) {
            throw new IllegalArgumentException("delivery failure values are invalid");
        }
    }
}
