package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "watch_target_id", nullable = false)
    private UUID watchTargetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_mode", nullable = false, length = 32)
    private NotificationMode notificationMode;

    @Column(name = "target_price_minor")
    private Long targetPriceMinor;

    @Column(name = "notification_reference_price_minor")
    private Long notificationReferencePriceMinor;

    @Column(name = "last_processed_price_observed_at")
    private Instant lastProcessedPriceObservedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_state", nullable = false, length = 32)
    private ThresholdState thresholdState;

    @Column(name = "threshold_observed_at")
    private Instant thresholdObservedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "price_history_started_at", nullable = false)
    private Instant priceHistoryStartedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SubscriptionEntity() {
    }

    public SubscriptionEntity(
            UUID id,
            UUID userId,
            UUID watchTargetId,
            NotificationMode notificationMode,
            Long targetPriceMinor,
            Long notificationReferencePriceMinor,
            Instant lastProcessedPriceObservedAt,
            ThresholdState thresholdState,
            Instant thresholdObservedAt,
            SubscriptionStatus status,
            Instant createdAt,
            Instant priceHistoryStartedAt,
            Instant endedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.watchTargetId = watchTargetId;
        this.notificationMode = notificationMode;
        this.targetPriceMinor = targetPriceMinor;
        this.notificationReferencePriceMinor = notificationReferencePriceMinor;
        this.lastProcessedPriceObservedAt = lastProcessedPriceObservedAt;
        this.thresholdState = thresholdState;
        this.thresholdObservedAt = thresholdObservedAt;
        this.status = status;
        this.createdAt = createdAt;
        this.priceHistoryStartedAt = priceHistoryStartedAt;
        this.endedAt = endedAt;
    }

    public void end(Instant endedAt) {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("only active subscription can be ended");
        }
        if (endedAt == null || endedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("endedAt must not be before createdAt");
        }
        this.status = SubscriptionStatus.ENDED;
        this.endedAt = endedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }

    public NotificationMode getNotificationMode() {
        return notificationMode;
    }

    public Long getTargetPriceMinor() {
        return targetPriceMinor;
    }

    public Long getNotificationReferencePriceMinor() {
        return notificationReferencePriceMinor;
    }

    public Instant getLastProcessedPriceObservedAt() {
        return lastProcessedPriceObservedAt;
    }

    public ThresholdState getThresholdState() {
        return thresholdState;
    }

    public Instant getThresholdObservedAt() {
        return thresholdObservedAt;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPriceHistoryStartedAt() {
        return priceHistoryStartedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public long getVersion() {
        return version;
    }
}
