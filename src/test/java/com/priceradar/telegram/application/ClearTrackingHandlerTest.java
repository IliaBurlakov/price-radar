package com.priceradar.telegram.application;

import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.tracking.application.ClearSubscriptionsPlan;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClearTrackingHandlerTest {

    private static final long TELEGRAM_ID = 7001L;

    @Test
    void clearConfirmationUsesRussianSingularFormsAndRequiresASecondClick() {
        assertConfirmation(1, "Сейчас отслеживается 1 товар.");
        assertConfirmation(2, "Сейчас отслеживается 2 товара.");
        assertConfirmation(5, "Сейчас отслеживается 5 товаров.");
    }

    private void assertConfirmation(int count, String expectedText) {
        UserProfileService users = mock(UserProfileService.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile(
                userId, TELEGRAM_ID, TELEGRAM_ID, moscow(), UserPricePreferences.defaults()
        );
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        when(subscriptions.prepareClearAll(userId)).thenReturn(
                new ClearSubscriptionsPlan(count, "fingerprint" + count)
        );
        TrackedItemsMessageHandler handler = new TrackedItemsMessageHandler(
                users, subscriptions,
                new TrackedItemsMessageFactory(new WalletEstimateService()),
                new ClearTrackingCallbackCodec(
                        "test-only-callback-secret-with-more-than-32-bytes"
                ),
                mock(PendingTargetPriceStore.class),
                gateway, Clock.systemUTC()
        );

        handler.handleCallback(new IncomingTelegramCallback(
                "callback", TELEGRAM_ID, TELEGRAM_ID, "private",
                ClearTrackingCallbackData.START
        ));

        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).contains(expectedText);
        assertThat(message.getValue().getInlineKeyboard()).flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .containsExactly("Да, очистить все", "← Назад");
        verify(subscriptions, org.mockito.Mockito.never()).clearAll(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
