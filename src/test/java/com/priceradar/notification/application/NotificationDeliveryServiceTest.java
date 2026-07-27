package com.priceradar.notification.application;

import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.telegram.application.OutgoingTelegramMessage;
import com.priceradar.telegram.application.TelegramDeliveryException;
import com.priceradar.telegram.application.TelegramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T06:00:00Z");
    private static final Duration CLAIM_TIMEOUT = Duration.ofSeconds(30);

    @Mock
    private NotificationDeliveryStore deliveryStore;

    @Mock
    private NotificationMessageRenderer messageRenderer;

    @Mock
    private TelegramGateway telegramGateway;

    private NotificationDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new NotificationDeliveryService(
                deliveryStore,
                messageRenderer,
                telegramGateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                25,
                5,
                CLAIM_TIMEOUT,
                Duration.ofSeconds(10),
                Duration.ofMinutes(15)
        );
    }

    @Test
    void sendsClaimedNotificationAndMarksItSent() {
        PendingNotificationDelivery notification = notification(true, 0);
        OutgoingTelegramMessage message = OutgoingTelegramMessage.text(7001L, "message");
        dueAndClaimed(notification);
        when(messageRenderer.render(notification)).thenReturn(message);
        when(deliveryStore.markSent(
                notification.getOutboxId(),
                NOW.plus(CLAIM_TIMEOUT),
                NOW
        )).thenReturn(true);

        deliveryService.deliverDue();

        verify(telegramGateway).sendMessage(message);
        verify(deliveryStore).markSent(
                notification.getOutboxId(),
                NOW.plus(CLAIM_TIMEOUT),
                NOW
        );
    }

    @Test
    void schedulesRetryWithExponentialBackoffForTemporaryTelegramFailure() {
        PendingNotificationDelivery notification = notification(true, 1);
        OutgoingTelegramMessage message = OutgoingTelegramMessage.text(7001L, "message");
        dueAndClaimed(notification);
        when(messageRenderer.render(notification)).thenReturn(message);
        doThrow(new TelegramDeliveryException(
                "temporary",
                true,
                Optional.of(Duration.ofMinutes(1))
        ))
                .when(telegramGateway).sendMessage(message);

        deliveryService.deliverDue();

        verify(deliveryStore).markRetry(
                notification.getOutboxId(),
                NOW.plus(CLAIM_TIMEOUT),
                2,
                NOW.plusSeconds(60),
                "TELEGRAM_TEMPORARY"
        );
        verify(deliveryStore, never()).markSent(any(), any(), any());
    }

    @Test
    void marksPermanentTelegramFailureWithoutRetrying() {
        PendingNotificationDelivery notification = notification(true, 0);
        OutgoingTelegramMessage message = OutgoingTelegramMessage.text(7001L, "message");
        dueAndClaimed(notification);
        when(messageRenderer.render(notification)).thenReturn(message);
        doThrow(new TelegramDeliveryException("rejected", false))
                .when(telegramGateway).sendMessage(message);

        deliveryService.deliverDue();

        verify(deliveryStore).markFailed(
                notification.getOutboxId(),
                NOW.plus(CLAIM_TIMEOUT),
                1,
                "TELEGRAM_PERMANENT"
        );
        verify(deliveryStore, never()).markRetry(any(), any(), anyInt(), any(), any());
    }

    @Test
    void doesNotSendNotificationForEndedSubscription() {
        PendingNotificationDelivery notification = notification(false, 0);
        dueAndClaimed(notification);

        deliveryService.deliverDue();

        verify(telegramGateway, never()).sendMessage(any());
        verify(deliveryStore).markFailed(
                notification.getOutboxId(),
                NOW.plus(CLAIM_TIMEOUT),
                1,
                "SUBSCRIPTION_ENDED"
        );
    }

    private void dueAndClaimed(PendingNotificationDelivery notification) {
        when(deliveryStore.findDue(NOW, 25)).thenReturn(List.of(notification));
        when(deliveryStore.claim(
                notification.getOutboxId(),
                notification.getNextAttemptAt(),
                NOW,
                NOW.plus(CLAIM_TIMEOUT)
        )).thenReturn(true);
        lenient().when(deliveryStore.markRetry(any(), any(), anyInt(), any(), any())).thenReturn(true);
        lenient().when(deliveryStore.markFailed(any(), any(), anyInt(), any())).thenReturn(true);
    }

    private PendingNotificationDelivery notification(boolean active, int attemptCount) {
        return new PendingNotificationDelivery(
                UUID.randomUUID(),
                NotificationType.PRICE_DECREASE,
                attemptCount,
                NOW.minusSeconds(1),
                active,
                7001L,
                123456L,
                Optional.of("Кофемолка"),
                Optional.empty(),
                Optional.empty(),
                "Moscow",
                "https://www.wildberries.ru/catalog/123456/detail.aspx",
                3,
                Optional.empty(),
                Optional.of(RubleAmount.ofMinorUnits(12_000L)),
                RubleAmount.ofMinorUnits(10_000L),
                NOW.minusSeconds(1)
        );
    }
}
