package com.priceradar.telegram.application;

import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.sharedbasket.application.SharedBasketApplyResult;
import com.priceradar.sharedbasket.application.SharedBasketImportService;
import com.priceradar.sharedbasket.application.SharedBasketPreview;
import com.priceradar.sharedbasket.application.SharedBasketUrlParser;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramSharedBasketHandlerTest {

    @Test
    void firstSyncClickShowsDestructiveConfirmationWithoutApplyingChanges() {
        Instant now = Instant.parse("2026-08-24T10:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        long telegramUserId = 7001L;
        SharedBasketImportService service = mock(SharedBasketImportService.class);
        UserProfileService users = mock(UserProfileService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        SharedBasketCallbackCodec codec = new SharedBasketCallbackCodec("01234567890123456789012345678901");
        UserProfile profile = new UserProfile(
                userId, telegramUserId, telegramUserId,
                new PriceContext("Moscow", 1259570991L, 30), UserPricePreferences.defaults()
        );
        SharedBasketPreview preview = new SharedBasketPreview(
                importId, 30, 30, 0, 15, 15, 8, 27, 15, 30,
                List.of("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight")
        );
        when(users.getOrCreate(telegramUserId, telegramUserId)).thenReturn(profile);
        when(service.findPreview(importId, userId, now)).thenReturn(Optional.of(preview));
        TelegramSharedBasketHandler handler = new TelegramSharedBasketHandler(
                new SharedBasketUrlParser(), service, codec, users, gateway,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        handler.handleCallback(new IncomingTelegramCallback(
                "callback", telegramUserId, telegramUserId, "private",
                codec.encode(SharedBasketCallbackCodec.Action.SYNC, importId, telegramUserId)
        ));

        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).contains("Подтвердите синхронизацию", "и ещё 3");
        verify(service, never()).apply(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void expiredCallbackOnlyAsksForFreshLinkAndNeverAppliesImport() {
        Instant now = Instant.parse("2026-08-24T10:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        long telegramUserId = 7001L;
        SharedBasketImportService service = mock(SharedBasketImportService.class);
        UserProfileService users = mock(UserProfileService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        SharedBasketCallbackCodec codec = new SharedBasketCallbackCodec("01234567890123456789012345678901");
        when(users.getOrCreate(telegramUserId, telegramUserId)).thenReturn(new UserProfile(
                userId, telegramUserId, telegramUserId,
                new PriceContext("Moscow", 1259570991L, 30), UserPricePreferences.defaults()
        ));
        when(service.findPreview(importId, userId, now)).thenReturn(Optional.empty());
        TelegramSharedBasketHandler handler = new TelegramSharedBasketHandler(
                new SharedBasketUrlParser(), service, codec, users, gateway,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        handler.handleCallback(new IncomingTelegramCallback(
                "callback", telegramUserId, telegramUserId, "private",
                codec.encode(SharedBasketCallbackCodec.Action.ADD, importId, telegramUserId)
        ));

        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).contains("Импорт устарел", "Отправьте ссылку");
        verify(service, never()).apply(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
        verify(service, never()).prepare(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
