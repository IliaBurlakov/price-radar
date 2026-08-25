package com.priceradar.telegram.application;

import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.LatestSnapshotQueryService;
import com.priceradar.tracking.application.LatestSnapshotView;
import com.priceradar.tracking.application.SubscriptionEndResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramItemNavigationTest {

    private static final long TELEGRAM_ID = 7001L;

    @Test
    void lastKnownPriceBackReopensTheExactTrackedItem() {
        TestContext context = context();
        TrackedSubscriptionItem first = item(UUID.randomUUID(), "Первый товар", 111_100L);
        TrackedSubscriptionItem selected = item(UUID.randomUUID(), "Выбранный товар", 222_200L);
        when(context.subscriptionService.findActive(context.profile.getId()))
                .thenReturn(List.of(first, selected));
        when(context.latestSnapshotQueryService.findLatest(
                context.profile.getId(), selected.getSubscriptionId()
        )).thenReturn(Optional.of(latestSnapshot(selected)));

        context.lastKnownHandler.handleCallback(callback(
                SubscriptionCallbackData.encode(
                        SubscriptionCallbackData.Action.SHOW_LAST_KNOWN,
                        selected.getSubscriptionId()
                )
        ));

        OutgoingTelegramMessage latest = capturedMessage(context.telegramGateway);
        TelegramInlineButton back = button(latest, "← Назад");
        assertThat(SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.OPEN_ITEM,
                back.getCallbackData()
        )).contains(selected.getSubscriptionId());

        clearInvocations(context.telegramGateway);
        context.trackedItemsHandler.handleCallback(callback(back.getCallbackData()));

        assertThat(capturedMessage(context.telegramGateway).getText())
                .startsWith("2. Выбранный товар")
                .doesNotContain("Первый товар");
    }

    @Test
    void removalConfirmationBackKeepsSubscriptionActiveAndReturnsToItem() {
        TestContext context = context();
        TrackedSubscriptionItem selected = item(UUID.randomUUID(), "Сумерки", 122_200L);
        when(context.subscriptionService.findActive(context.profile.getId()))
                .thenReturn(List.of(selected));

        context.trackedItemsHandler.handleCallback(callback(
                SubscriptionCallbackData.encode(
                        SubscriptionCallbackData.Action.REMOVE,
                        selected.getSubscriptionId()
                )
        ));

        OutgoingTelegramMessage confirmation = capturedMessage(context.telegramGateway);
        assertThat(confirmation.getText()).contains("Прекратить отслеживание?", "Сумерки");
        verify(context.subscriptionService, never()).end(any(), any(), any());

        clearInvocations(context.telegramGateway);
        context.trackedItemsHandler.handleCallback(callback(
                button(confirmation, "← Назад").getCallbackData()
        ));

        assertThat(capturedMessage(context.telegramGateway).getText())
                .startsWith("1. Сумерки");
        verify(context.subscriptionService, never()).end(any(), any(), any());

        when(context.subscriptionService.end(
                org.mockito.ArgumentMatchers.eq(context.profile.getId()),
                org.mockito.ArgumentMatchers.eq(selected.getSubscriptionId()),
                any()
        )).thenReturn(SubscriptionEndResult.notFound());
        clearInvocations(context.telegramGateway);
        context.trackedItemsHandler.handleCallback(callback(
                button(confirmation, "Да, удалить").getCallbackData()
        ));

        verify(context.subscriptionService).end(
                org.mockito.ArgumentMatchers.eq(context.profile.getId()),
                org.mockito.ArgumentMatchers.eq(selected.getSubscriptionId()),
                any()
        );
    }

    private TestContext context() {
        UserProfile profile = new UserProfile(
                UUID.randomUUID(), TELEGRAM_ID, TELEGRAM_ID,
                new PriceContext("Moscow", 1259570991L, 30),
                UserPricePreferences.defaults()
        );
        UserProfileService users = mock(UserProfileService.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        LatestSnapshotQueryService latestSnapshots = mock(LatestSnapshotQueryService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        TrackedItemsMessageHandler trackedItems = new TrackedItemsMessageHandler(
                users,
                subscriptions,
                new TrackedItemsMessageFactory(new WalletEstimateService()),
                gateway,
                Clock.systemUTC()
        );
        ShowLastKnownCallbackHandler lastKnown = new ShowLastKnownCallbackHandler(
                users,
                latestSnapshots,
                new LatestSnapshotMessageFactory(new WalletEstimateService()),
                gateway
        );
        return new TestContext(
                profile, subscriptions, latestSnapshots, gateway, trackedItems, lastKnown
        );
    }

    private LatestSnapshotView latestSnapshot(TrackedSubscriptionItem item) {
        return new LatestSnapshotView(
                item.getSubscriptionId(),
                item.getNmId(),
                item.getTitle(),
                item.getBrand(),
                item.getCanonicalUrl(),
                item.getVariantDisplayName(),
                new PriceContext("Moscow", 1259570991L, 30),
                Optional.empty(),
                Optional.empty()
        );
    }

    private TrackedSubscriptionItem item(UUID id, String title, long priceMinor) {
        Instant observedAt = Instant.parse("2026-08-25T10:00:00Z");
        return new TrackedSubscriptionItem(
                id,
                123456L,
                Optional.of(title),
                Optional.of("Бренд"),
                "https://www.wildberries.ru/catalog/123456/detail.aspx",
                Optional.empty(),
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                observedAt.minusSeconds(60),
                Optional.of(SnapshotStatus.REGULAR_PRICE),
                Optional.of(RubleAmount.ofMinorUnits(priceMinor)),
                Optional.of(observedAt)
        );
    }

    private IncomingTelegramCallback callback(String data) {
        return new IncomingTelegramCallback(
                "callback", TELEGRAM_ID, TELEGRAM_ID, "private", data
        );
    }

    private OutgoingTelegramMessage capturedMessage(TelegramGateway gateway) {
        var captor = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(captor.capture());
        return captor.getValue();
    }

    private TelegramInlineButton button(OutgoingTelegramMessage message, String text) {
        return message.getInlineKeyboard().stream()
                .flatMap(List::stream)
                .filter(button -> button.getText().equals(text))
                .findFirst()
                .orElseThrow();
    }

    private static final class TestContext {
        private final UserProfile profile;
        private final SubscriptionService subscriptionService;
        private final LatestSnapshotQueryService latestSnapshotQueryService;
        private final TelegramGateway telegramGateway;
        private final TrackedItemsMessageHandler trackedItemsHandler;
        private final ShowLastKnownCallbackHandler lastKnownHandler;

        private TestContext(
                UserProfile profile,
                SubscriptionService subscriptionService,
                LatestSnapshotQueryService latestSnapshotQueryService,
                TelegramGateway telegramGateway,
                TrackedItemsMessageHandler trackedItemsHandler,
                ShowLastKnownCallbackHandler lastKnownHandler
        ) {
            this.profile = profile;
            this.subscriptionService = subscriptionService;
            this.latestSnapshotQueryService = latestSnapshotQueryService;
            this.telegramGateway = telegramGateway;
            this.trackedItemsHandler = trackedItemsHandler;
            this.lastKnownHandler = lastKnownHandler;
        }
    }
}
