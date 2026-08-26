package com.priceradar.telegram.application;

import com.priceradar.tracking.application.SubscriptionPreparationResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;

class TelegramTrackingNavigationTest {

    @Test
    void targetPriceInputOffersSignedCancellationWithoutAnUnsafeHomeShortcut() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        long telegramId = 7001L;
        UUID quoteSnapshotId = UUID.randomUUID();
        UserProfile profile = new UserProfile(
                UUID.randomUUID(), telegramId, telegramId,
                moscow(),
                UserPricePreferences.defaults()
        );
        UserProfileService users = mock(UserProfileService.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        PendingTargetPriceStore pendingStore = mock(PendingTargetPriceStore.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        TrackingCallbackCodec codec = new TrackingCallbackCodec(
                "test-only-callback-secret-with-more-than-32-bytes"
        );
        when(users.getOrCreate(telegramId, telegramId)).thenReturn(profile);
        when(subscriptions.prepareFromQuote(profile.getId(), quoteSnapshotId, now))
                .thenReturn(SubscriptionPreparationResult.ready());
        TelegramTrackingHandler handler = new TelegramTrackingHandler(
                users, subscriptions, new TargetPriceParser(), codec,
                pendingStore, gateway, Clock.fixed(now, ZoneOffset.UTC)
        );

        handler.handleCallback(new IncomingTelegramCallback(
                "target", telegramId, telegramId, "private",
                codec.encode(
                        TrackingCallbackData.Action.TRACK_TARGET,
                        quoteSnapshotId,
                        telegramId
                )
        ));

        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getInlineKeyboard())
                .flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .containsExactly("Отмена");
        String callbackData = message.getValue().getInlineKeyboard().getFirst().getFirst()
                .getCallbackData();
        assertThat(codec.decode(callbackData, telegramId))
                .get()
                .extracting(TrackingCallbackData::getAction)
                .isEqualTo(TrackingCallbackData.Action.CANCEL_TARGET);
    }

    @Test
    void cancelTargetPriceInputRemovesMatchingPendingState() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        long telegramId = 7001L;
        UUID quoteSnapshotId = UUID.randomUUID();
        PendingTargetPrice pending = new PendingTargetPrice(
                telegramId, telegramId, quoteSnapshotId, now.plusSeconds(300)
        );
        PendingTargetPriceStore pendingStore = mock(PendingTargetPriceStore.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        TrackingCallbackCodec codec = new TrackingCallbackCodec(
                "test-only-callback-secret-with-more-than-32-bytes"
        );
        when(pendingStore.find(telegramId, telegramId, now))
                .thenReturn(Optional.of(pending));
        TelegramTrackingHandler handler = new TelegramTrackingHandler(
                mock(UserProfileService.class),
                mock(SubscriptionService.class),
                new TargetPriceParser(),
                codec,
                pendingStore,
                gateway,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        handler.handleCallback(new IncomingTelegramCallback(
                "cancel", telegramId, telegramId, "private",
                codec.encode(
                        TrackingCallbackData.Action.CANCEL_TARGET,
                        quoteSnapshotId,
                        telegramId
                )
        ));

        verify(pendingStore).remove(pending);
        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).isEqualTo("Ввод желаемой цены отменён.");
    }
}
