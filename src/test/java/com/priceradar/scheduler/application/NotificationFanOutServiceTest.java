package com.priceradar.scheduler.application;

import com.priceradar.notification.application.NotificationObservation;
import com.priceradar.tracking.application.SubscriptionStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationFanOutServiceTest {

    @Test
    void oneBrokenSubscriptionDoesNotPreventProcessingTheOthers() {
        SubscriptionStore store = mock(SubscriptionStore.class);
        SubscriptionNotificationProcessor processor = mock(SubscriptionNotificationProcessor.class);
        NotificationObservation observation = mock(NotificationObservation.class);
        UUID watchTargetId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-07-19T08:00:00Z");
        when(observation.getWatchTargetId()).thenReturn(watchTargetId);
        when(observation.getSnapshotId()).thenReturn(UUID.randomUUID());
        when(store.findActiveIdsByWatchTargetId(watchTargetId))
                .thenReturn(List.of(firstId, secondId));
        doThrow(new IllegalStateException("broken subscription"))
                .when(processor).process(firstId, observation, completedAt);
        NotificationFanOutService service = new NotificationFanOutService(store, processor);

        assertThatThrownBy(() -> service.process(observation, completedAt))
                .isInstanceOf(IllegalStateException.class);

        verify(processor).process(secondId, observation, completedAt);
    }
}
