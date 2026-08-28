package com.priceradar.feedback.domain;

import java.time.Instant;
import java.util.UUID;

public final class FeedbackMessage {

    private final UUID id;
    private final UUID userId;
    private final String message;
    private final Instant createdAt;

    public FeedbackMessage(UUID id, UUID userId, String message, Instant createdAt) {
        if (id == null || userId == null || createdAt == null) {
            throw new IllegalArgumentException("feedback fields must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("feedback message must not be blank");
        }
        this.id = id;
        this.userId = userId;
        this.message = message.trim();
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
