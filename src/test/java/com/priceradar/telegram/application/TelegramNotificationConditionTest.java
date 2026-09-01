package com.priceradar.telegram.application;

import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.SubscriptionConditionChangeResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramNotificationConditionTest {

    private static final long TELEGRAM_ID = 7001L;
    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");

    @Test
    void conditionScreenShowsCurrentModeAndReturnsToTheSameItem() {
        TrackedItemsMessageFactory factory = new TrackedItemsMessageFactory(
                new WalletEstimateService()
        );
        TrackedSubscriptionItem minimum = item(NotificationMode.ANY_DECREASE, Optional.empty());
        TrackedSubscriptionItem target = item(
                NotificationMode.TARGET_PRICE,
                Optional.of(RubleAmount.ofMinorUnits(35_000))
        );

        OutgoingTelegramMessage minimumScreen = factory.createNotificationCondition(
                TELEGRAM_ID, minimum
        );
        OutgoingTelegramMessage targetScreen = factory.createNotificationCondition(
                TELEGRAM_ID, target
        );

        assertThat(minimumScreen.getText()).contains("📉 Новая минимальная цена");
        assertThat(buttonTexts(minimumScreen)).containsExactly(
                "✓ 📉 Новая минимальная цена",
                "🎯 Установить целевую цену",
                "← Назад"
        );
        assertThat(targetScreen.getText()).contains("🎯 Цена не выше 350 ₽");
        assertThat(buttonTexts(targetScreen)).containsExactly(
                "📉 Новая минимальная цена",
                "🎯 Изменить целевую цену",
                "← Назад"
        );
        assertThat(targetScreen.getInlineKeyboard().getLast().getFirst().getCallbackData())
                .isEqualTo(SubscriptionCallbackData.encode(
                        SubscriptionCallbackData.Action.OPEN_ITEM,
                        target.getSubscriptionId()
                ));
    }

    @Test
    void targetEditUsesDedicatedPendingPurposeAndUpdatesExistingSubscription() {
        UserProfile profile = profile();
        UUID subscriptionId = UUID.randomUUID();
        PendingTargetPrice pending = new PendingTargetPrice(
                TELEGRAM_ID,
                TELEGRAM_ID,
                PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION,
                subscriptionId,
                NOW.plusSeconds(900)
        );
        UserProfileService users = mock(UserProfileService.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        PendingTargetPriceStore pendingStore = mock(PendingTargetPriceStore.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        when(pendingStore.find(TELEGRAM_ID, TELEGRAM_ID, NOW))
                .thenReturn(Optional.of(pending));
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        Subscription changed = targetSubscription(profile.getId(), subscriptionId, 35_000);
        when(subscriptions.changeToTargetPrice(
                profile.getId(), subscriptionId, RubleAmount.ofMinorUnits(35_000), NOW
        )).thenReturn(SubscriptionConditionChangeResult.changed(changed));
        TelegramTrackingHandler handler = new TelegramTrackingHandler(
                users,
                subscriptions,
                new TargetPriceParser(),
                new TrackingCallbackCodec("test-only-callback-secret-with-more-than-32-bytes"),
                pendingStore,
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC), java.time.Duration.ofMinutes(15), 50
        );

        boolean handled = handler.handleTargetPriceInput(new IncomingTelegramMessage(
                TELEGRAM_ID, TELEGRAM_ID, "private", "350"
        ));

        assertThat(handled).isTrue();
        verify(subscriptions).changeToTargetPrice(
                profile.getId(), subscriptionId, RubleAmount.ofMinorUnits(35_000), NOW
        );
        verify(subscriptions, never()).createFromQuote(any(), any(), any(), any(), any());
        verify(pendingStore).remove(pending);
        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText())
                .contains("Условие уведомлений изменено", "не выше 350 ₽");
        assertThat(message.getValue().getInlineKeyboard().getFirst().getFirst().getCallbackData())
                .isEqualTo(SubscriptionCallbackData.encode(
                        SubscriptionCallbackData.Action.OPEN_ITEM,
                        subscriptionId
                ));
    }

    @Test
    void targetButtonStartsEditForTheSelectedActiveSubscription() {
        UserProfile profile = profile();
        TrackedSubscriptionItem item = item(NotificationMode.ANY_DECREASE, Optional.empty());
        UserProfileService users = mock(UserProfileService.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        PendingTargetPriceStore pendingStore = mock(PendingTargetPriceStore.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        when(subscriptions.findActive(profile.getId())).thenReturn(List.of(item));
        TrackedItemsMessageHandler handler = new TrackedItemsMessageHandler(
                users,
                subscriptions,
                new TrackedItemsMessageFactory(new WalletEstimateService()),
                new ClearTrackingCallbackCodec(
                        "test-only-callback-secret-with-more-than-32-bytes"
                ),
                pendingStore,
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC), java.time.Duration.ofMinutes(15)
        );

        handler.handleCallback(new IncomingTelegramCallback(
                "edit-target",
                TELEGRAM_ID,
                TELEGRAM_ID,
                "private",
                SubscriptionCallbackData.encode(
                        SubscriptionCallbackData.Action.EDIT_TARGET_PRICE,
                        item.getSubscriptionId()
                )
        ));

        var pending = org.mockito.ArgumentCaptor.forClass(PendingTargetPrice.class);
        verify(pendingStore).put(pending.capture(), org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(pending.getValue().getPurpose())
                .isEqualTo(PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION);
        assertThat(pending.getValue().requireSubscriptionId())
                .isEqualTo(item.getSubscriptionId());
        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).contains("Введите желаемую цену");
    }

    @Test
    void targetAtOrAboveQuoteRegularPriceKeepsCreateInputPending() {
        UserProfile profile = profile();
        UUID quoteSnapshotId = UUID.randomUUID();
        PendingTargetPrice pending = new PendingTargetPrice(
                TELEGRAM_ID, TELEGRAM_ID,
                PendingTargetPrice.Purpose.CREATE_SUBSCRIPTION,
                quoteSnapshotId, NOW.plusSeconds(900)
        );
        UserProfileService users = mock(UserProfileService.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        PendingTargetPriceStore pendingStore = mock(PendingTargetPriceStore.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        when(pendingStore.find(TELEGRAM_ID, TELEGRAM_ID, NOW))
                .thenReturn(Optional.of(pending));
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        when(subscriptions.findQuoteRegularPrice(profile.getId(), quoteSnapshotId, NOW))
                .thenReturn(Optional.of(RubleAmount.ofMinorUnits(47_900)));
        TelegramTrackingHandler handler = trackingHandler(
                users, subscriptions, pendingStore, gateway
        );

        assertThat(handler.handleTargetPriceInput(message("479"))).isTrue();

        verify(subscriptions, never()).createFromQuote(any(), any(), any(), any(), any());
        verify(pendingStore, never()).remove(pending);
        assertThat(captured(gateway).getText()).isEqualTo(
                "⚠️ Сейчас товар уже стоит 479 ₽.\nВведите желаемую цену ниже текущей."
        );
    }

    @Test
    void targetAboveLatestSubscriptionRegularPriceKeepsEditInputPending() {
        UserProfile profile = profile();
        UUID subscriptionId = UUID.randomUUID();
        PendingTargetPrice pending = new PendingTargetPrice(
                TELEGRAM_ID, TELEGRAM_ID,
                PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION,
                subscriptionId, NOW.plusSeconds(900)
        );
        UserProfileService users = mock(UserProfileService.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        PendingTargetPriceStore pendingStore = mock(PendingTargetPriceStore.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        when(pendingStore.find(TELEGRAM_ID, TELEGRAM_ID, NOW))
                .thenReturn(Optional.of(pending));
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        when(subscriptions.findLatestRegularPrice(profile.getId(), subscriptionId, NOW))
                .thenReturn(Optional.of(RubleAmount.ofMinorUnits(47_900)));
        TelegramTrackingHandler handler = trackingHandler(
                users, subscriptions, pendingStore, gateway
        );

        assertThat(handler.handleTargetPriceInput(message("500"))).isTrue();

        verify(subscriptions, never()).changeToTargetPrice(any(), any(), any(), any());
        verify(pendingStore, never()).remove(pending);
        assertThat(captured(gateway).getText())
                .contains("Сейчас товар уже стоит 479 ₽", "ниже текущей");
    }

    @Test
    void invalidEditPriceKeepsEditPendingAndUsesTheExistingValidationMessage() {
        UUID subscriptionId = UUID.randomUUID();
        PendingTargetPrice pending = new PendingTargetPrice(
                TELEGRAM_ID,
                TELEGRAM_ID,
                PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION,
                subscriptionId,
                NOW.plusSeconds(900)
        );
        PendingTargetPriceStore pendingStore = mock(PendingTargetPriceStore.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        when(pendingStore.find(TELEGRAM_ID, TELEGRAM_ID, NOW))
                .thenReturn(Optional.of(pending));
        TelegramTrackingHandler handler = new TelegramTrackingHandler(
                mock(UserProfileService.class),
                subscriptions,
                new TargetPriceParser(),
                new TrackingCallbackCodec("test-only-callback-secret-with-more-than-32-bytes"),
                pendingStore,
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC), java.time.Duration.ofMinutes(15), 50
        );

        boolean handled = handler.handleTargetPriceInput(new IncomingTelegramMessage(
                TELEGRAM_ID, TELEGRAM_ID, "private", "не цена"
        ));

        assertThat(handled).isTrue();
        verify(subscriptions, never()).changeToTargetPrice(any(), any(), any(), any());
        verify(pendingStore, never()).remove(pending);
        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).isEqualTo(
                "Введите цену в рублях, например 1500."
        );
        assertThat(message.getValue().getInlineKeyboard().getFirst().getFirst().getCallbackData())
                .isEqualTo(SubscriptionCallbackData.encode(
                        SubscriptionCallbackData.Action.CANCEL_CONDITION_EDIT,
                        subscriptionId
                ));
    }

    @Test
    void staleConditionCallbackCannotEditEndedOrForeignSubscription() {
        UserProfile profile = profile();
        UUID subscriptionId = UUID.randomUUID();
        UserProfileService users = mock(UserProfileService.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        when(subscriptions.changeToAnyDecrease(profile.getId(), subscriptionId, NOW))
                .thenReturn(SubscriptionConditionChangeResult.of(
                        SubscriptionConditionChangeResult.Status.NOT_FOUND
                ));
        TrackedItemsMessageHandler handler = new TrackedItemsMessageHandler(
                users,
                subscriptions,
                new TrackedItemsMessageFactory(new WalletEstimateService()),
                new ClearTrackingCallbackCodec(
                        "test-only-callback-secret-with-more-than-32-bytes"
                ),
                mock(PendingTargetPriceStore.class),
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC), java.time.Duration.ofMinutes(15)
        );

        handler.handleCallback(new IncomingTelegramCallback(
                "condition",
                TELEGRAM_ID,
                TELEGRAM_ID,
                "private",
                SubscriptionCallbackData.encode(
                        SubscriptionCallbackData.Action.SET_ANY_DECREASE,
                        subscriptionId
                )
        ));

        verify(subscriptions).changeToAnyDecrease(profile.getId(), subscriptionId, NOW);
        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).contains("больше не отслеживается");
    }

    private List<String> buttonTexts(OutgoingTelegramMessage message) {
        return message.getInlineKeyboard().stream()
                .flatMap(List::stream)
                .map(TelegramInlineButton::getText)
                .toList();
    }

    private UserProfile profile() {
        return new UserProfile(
                UUID.randomUUID(), TELEGRAM_ID, TELEGRAM_ID,
                moscow(), UserPricePreferences.defaults()
        );
    }

    private TelegramTrackingHandler trackingHandler(
            UserProfileService users,
            SubscriptionService subscriptions,
            PendingTargetPriceStore pendingStore,
            TelegramGateway gateway
    ) {
        return new TelegramTrackingHandler(
                users,
                subscriptions,
                new TargetPriceParser(),
                new TrackingCallbackCodec(
                        "test-only-callback-secret-with-more-than-32-bytes"
                ),
                pendingStore,
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                java.time.Duration.ofMinutes(15),
                50
        );
    }

    private IncomingTelegramMessage message(String text) {
        return new IncomingTelegramMessage(TELEGRAM_ID, TELEGRAM_ID, "private", text);
    }

    private OutgoingTelegramMessage captured(TelegramGateway gateway) {
        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        return message.getValue();
    }

    private TrackedSubscriptionItem item(
            NotificationMode mode,
            Optional<RubleAmount> targetPrice
    ) {
        return new TrackedSubscriptionItem(
                UUID.randomUUID(),
                123456L,
                Optional.of("Товар"),
                Optional.of("Бренд"),
                "https://www.wildberries.ru/catalog/123456/detail.aspx",
                Optional.empty(),
                mode,
                targetPrice,
                NOW.minusSeconds(3600),
                Optional.of(SnapshotStatus.REGULAR_PRICE),
                Optional.of(RubleAmount.ofMinorUnits(40_000)),
                Optional.of(NOW.minusSeconds(60))
        );
    }

    private Subscription targetSubscription(UUID userId, UUID subscriptionId, long targetMinor) {
        return new Subscription(
                subscriptionId,
                userId,
                UUID.randomUUID(),
                NotificationMode.TARGET_PRICE,
                Optional.of(RubleAmount.ofMinorUnits(targetMinor)),
                Optional.empty(),
                Optional.of(NOW.minusSeconds(60)),
                ThresholdState.ABOVE_TARGET,
                Optional.of(NOW.minusSeconds(60)),
                SubscriptionStatus.ACTIVE,
                NOW.minusSeconds(3600),
                Optional.empty(),
                1
        );
    }
}
