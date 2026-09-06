package com.priceradar.scheduler.application;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.domain.WatchKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchTargetCheckTransactionTest {

    @Test
    void fanOutJobFailurePreventsSuccessfulCheckCompletion() {
        ScheduledObservationStore observationStore = mock(ScheduledObservationStore.class);
        NotificationFanOutJobStore jobStore = mock(NotificationFanOutJobStore.class);
        WatchTargetCheckTransaction transaction = new WatchTargetCheckTransaction(
                observationStore,
                jobStore
        );
        Instant observedAt = Instant.parse("2026-09-05T08:00:00Z");
        Instant completedAt = observedAt.plusSeconds(1);
        Instant nextCheckAt = completedAt.plusSeconds(21_600);
        UUID snapshotId = UUID.randomUUID();
        UUID checkId = UUID.randomUUID();
        DueWatchTarget target = target();
        MarketplaceProductDetails product = product();
        InterpretedPrice price = regularPrice();
        when(observationStore.saveSnapshot(checkId, target.getWatchTargetId(), price, observedAt))
                .thenReturn(snapshotId);
        doThrow(new IllegalStateException("fan-out persistence failed"))
                .when(jobStore).createIfAbsent(snapshotId, completedAt);

        assertThatThrownBy(() -> transaction.persistObservation(
                target,
                checkId,
                product,
                price,
                observedAt,
                completedAt,
                nextCheckAt
        )).isInstanceOf(IllegalStateException.class);

        verify(observationStore, never()).markSuccessful(
                target.getWatchTargetId(),
                completedAt,
                nextCheckAt
        );
    }

    private DueWatchTarget target() {
        return new DueWatchTarget(
                UUID.randomUUID(),
                UUID.randomUUID(),
                WatchKey.withoutVariant(Marketplace.WILDBERRIES, 123L, 1259570991L, 30),
                Instant.parse("2026-09-05T07:00:00Z")
        );
    }

    private MarketplaceProductDetails product() {
        return new MarketplaceProductDetails(
                Marketplace.WILDBERRIES,
                "123",
                Optional.of("Test product"),
                Optional.of("Test brand"),
                List.of(),
                Map.of()
        );
    }

    private InterpretedPrice regularPrice() {
        return new InterpretedPrice(
                Optional.of(RubleAmount.ofMinorUnits(10_000)),
                Optional.empty(),
                Optional.of(PriceSource.PRODUCT),
                SnapshotStatus.REGULAR_PRICE
        );
    }
}
