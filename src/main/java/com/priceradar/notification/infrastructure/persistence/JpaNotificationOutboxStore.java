package com.priceradar.notification.infrastructure.persistence;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.priceradar.notification.application.NotificationIntent;
import com.priceradar.notification.application.NotificationOutboxStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class JpaNotificationOutboxStore implements NotificationOutboxStore {

    private final NotificationOutboxJpaRepository repository;

    public JpaNotificationOutboxStore(NotificationOutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean enqueueIfAbsent(
            NotificationIntent intent,
            String idempotencyKey,
            Instant createdAt
    ) {
        if (intent == null || idempotencyKey == null || idempotencyKey.isBlank()
                || createdAt == null) {
            throw new IllegalArgumentException("pending notification fields must not be null or blank");
        }
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("currentPriceMinor", intent.getCurrentPrice().getMinorUnits());
        intent.getPreviousPrice().ifPresent(previous ->
                payload.put("previousPriceMinor", previous.getMinorUnits()));
        payload.put("observedAt", intent.getObservedAt().toString());

        return repository.insertPending(
                UUID.randomUUID(),
                intent.getSubscriptionId(),
                intent.getSnapshotId(),
                intent.getType().name(),
                idempotencyKey,
                payload.toString(),
                createdAt,
                createdAt
        ) == 1;
    }
}
