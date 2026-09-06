package com.priceradar.telegram.application;

import com.priceradar.user.application.PendingWalletDiscountInput;
import com.priceradar.user.application.PendingWalletDiscountInputStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramWalletDiscountHandlerTest {

    private static final long TELEGRAM_ID = 7001L;
    private static final Instant NOW = Instant.parse("2026-08-31T08:00:00Z");

    @Test
    void opensWithCurrentValueAndPersistsPendingInput() {
        Context context = context();
        IncomingTelegramCallback callback = callback();

        assertThat(context.handler.handleCallback(callback)).isTrue();

        var pending = org.mockito.ArgumentCaptor.forClass(PendingWalletDiscountInput.class);
        verify(context.pendingStore).replace(pending.capture());
        assertThat(pending.getValue().getUserId()).isEqualTo(context.profile.getId());
        assertThat(pending.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(captured(context.gateway).getText())
                .contains(
                        "💳 WB Кошелёк",
                        "Текущая скидка: 3%",
                        "Введите размер скидки вашего WB Кошелька"
                );
    }

    @Test
    void invalidInputKeepsPendingAndValidInputUpdatesOnlyThePreference() {
        Context context = context();
        PendingWalletDiscountInput pending = new PendingWalletDiscountInput(
                context.profile.getId(), TELEGRAM_ID, TELEGRAM_ID,
                NOW, NOW.plusSeconds(900)
        );
        when(context.pendingStore.find(TELEGRAM_ID, TELEGRAM_ID))
                .thenReturn(Optional.of(pending));

        for (String input : new String[]{"текст", "1", "21"}) {
            assertThat(context.handler.handlePendingMessage(message(input))).isTrue();
        }

        verify(context.users, never()).updateWalletDiscountPercent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()
        );
        verify(context.pendingStore, never()).remove(TELEGRAM_ID, TELEGRAM_ID);

        assertThat(context.handler.handlePendingMessage(message("5"))).isTrue();

        verify(context.users).updateWalletDiscountPercent(TELEGRAM_ID, TELEGRAM_ID, 5);
        verify(context.pendingStore).remove(TELEGRAM_ID, TELEGRAM_ID);
        var messages = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway, times(4)).sendMessage(messages.capture());
        assertThat(messages.getAllValues().getLast().getText())
                .isEqualTo("✅ Скидка WB Кошелька изменена: 5%.");
    }

    private Context context() {
        UserProfileService users = mock(UserProfileService.class);
        PendingWalletDiscountInputStore pendingStore = mock(PendingWalletDiscountInputStore.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        UserProfile profile = new UserProfile(
                UUID.randomUUID(), TELEGRAM_ID, TELEGRAM_ID,
                moscow(), UserPricePreferences.defaults()
        );
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        TelegramWalletDiscountHandler handler = new TelegramWalletDiscountHandler(
                users, pendingStore, gateway, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15)
        );
        return new Context(handler, users, pendingStore, gateway, profile);
    }

    private IncomingTelegramCallback callback() {
        return new IncomingTelegramCallback(
                "wallet", TELEGRAM_ID, TELEGRAM_ID, "private",
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.WALLET_DISCOUNT)
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

    private static final class Context {
        private final TelegramWalletDiscountHandler handler;
        private final UserProfileService users;
        private final PendingWalletDiscountInputStore pendingStore;
        private final TelegramGateway gateway;
        private final UserProfile profile;

        private Context(
                TelegramWalletDiscountHandler handler,
                UserProfileService users,
                PendingWalletDiscountInputStore pendingStore,
                TelegramGateway gateway,
                UserProfile profile
        ) {
            this.handler = handler;
            this.users = users;
            this.pendingStore = pendingStore;
            this.gateway = gateway;
            this.profile = profile;
        }
    }
}
