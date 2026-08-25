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
                importId, 60, 60, 0, 15, 45, 8, 2, 27, 27, 50,
                List.of("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight"),
                List.of("Limited one", "Limited two"), "AbCdEf12345"
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
        assertThat(message.getValue().getText()).contains(
                "Подтвердите синхронизацию",
                "Сейчас вы отслеживаете 8 товаров",
                "которых нет в этой корзине",
                "Ещё 2 отслеживаемых товара есть в корзине",
                "• и ещё 3",
                "Некоторые товары могли отслеживаться уже несколько месяцев.",
                "При прекращении подписки текущий период отслеживания завершится.",
                "При повторном добавлении начнётся новый период отслеживания и статистики."
        ).doesNotContain("из-за лимита: 0", "во всей корзине");
        verify(service, never()).apply(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
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
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(service, never()).prepare(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }


    @Test
    void skippedItemsHideSynchronizationAndSignedSyncCallbackCannotApplyIt() {
        TestContext context = context();
        SharedBasketPreview preview = new SharedBasketPreview(
                context.importId, 3, 2, 1, 0, 2, 0, 0, 48, 2, 2,
                List.of(), List.of(), "AbCdEf12345"
        );
        when(context.service.findPreview(context.importId, context.userId, context.now))
                .thenReturn(Optional.of(preview));

        context.handler.handleCallback(new IncomingTelegramCallback(
                "callback", context.telegramUserId, context.telegramUserId, "private",
                context.codec.encode(SharedBasketCallbackCodec.Action.BACK, context.importId, context.telegramUserId)
        ));

        var previewMessage = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway).sendMessage(previewMessage.capture());
        assertThat(previewMessage.getValue().getText()).contains(
                "1 товар не удалось распознать.",
                "Он не будет добавлен.",
                "Безопасная синхронизация сейчас недоступна.",
                "Попробуйте отправить корзину ещё раз позже."
        );
        assertThat(previewMessage.getValue().getInlineKeyboard().stream()
                .flatMap(List::stream)
                .map(TelegramInlineButton::getText)
                .toList())
                .containsExactly("Добавить новые", "Отмена");
        org.mockito.Mockito.clearInvocations(context.gateway);

        context.handler.handleCallback(new IncomingTelegramCallback(
                "callback", context.telegramUserId, context.telegramUserId, "private",
                context.codec.encodeSyncConfirm(context.importId, "AbCdEf12345", context.telegramUserId)
        ));

        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).contains("Безопасная синхронизация недоступна");
        verify(context.service, never()).apply(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void changedPlanShowsAReplacementConfirmationInsteadOfApplyingNewDestructiveChanges() {
        TestContext context = context();
        SharedBasketPreview original = new SharedBasketPreview(
                context.importId, 1, 1, 0, 0, 1, 1, 0, 49, 1, 1,
                List.of("Old removal"), List.of(), "AbCdEf12345"
        );
        SharedBasketPreview refreshed = new SharedBasketPreview(
                context.importId, 1, 1, 0, 0, 1, 2, 0, 48, 1, 1,
                List.of("Old removal", "New removal"), List.of(), "ZyXwVu98765"
        );
        when(context.service.findPreview(context.importId, context.userId, context.now))
                .thenReturn(Optional.of(original), Optional.of(refreshed));
        when(context.service.apply(
                context.importId, context.userId, SharedBasketImportService.ApplyMode.SYNCHRONIZE,
                Optional.of("AbCdEf12345"), context.now
        )).thenReturn(SharedBasketApplyResult.failed(SharedBasketApplyResult.Status.PLAN_CHANGED));

        context.handler.handleCallback(new IncomingTelegramCallback(
                "callback", context.telegramUserId, context.telegramUserId, "private",
                context.codec.encodeSyncConfirm(context.importId, "AbCdEf12345", context.telegramUserId)
        ));

        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).contains(
                "Подтвердите синхронизацию", "Сейчас вы отслеживаете 2 товара", "New removal"
        ).doesNotContain("после первых 50", "из-за лимита: 0");
    }

    @Test
    void previewRendersOnlyRelevantCountsAndWarnings() {
        SharedBasketPreview happyPath = new SharedBasketPreview(
                UUID.randomUUID(), 3, 3, 0, 0, 3, 0, 0, 50, 3, 3,
                List.of(), List.of(), "AbCdEf12345"
        );
        String happyText = renderPreview(happyPath).getText();

        assertThat(happyText)
                .contains("Найдено товаров: 3", "Новых: 3")
                .doesNotContain(
                        "Готово к импорту",
                        "Уже отслеживаются: 0",
                        "не удалось распознать",
                        "Лимит отслеживания",
                        "При синхронизации бот перестанет"
                );

        SharedBasketPreview warnings = new SharedBasketPreview(
                UUID.randomUUID(), 73, 71, 2, 15, 56, 6, 2, 35, 35, 50,
                List.of("Outside"), List.of("Limited"), "ZyXwVu98765"
        );
        String warningText = renderPreview(warnings).getText();

        assertThat(warningText).contains(
                "Уже отслеживаются: 15",
                "При синхронизации бот перестанет отслеживать",
                "6 товаров, которых нет в этой корзине",
                "2 товара не удалось распознать",
                "Лимит отслеживания — 50 товаров",
                "21 товар из этой корзины не войдёт в список"
        ).doesNotContain("Готово к импорту", ": 0");
    }

    private OutgoingTelegramMessage renderPreview(SharedBasketPreview preview) {
        TestContext context = context();
        when(context.service.findPreview(context.importId, context.userId, context.now))
                .thenReturn(Optional.of(new SharedBasketPreview(
                        context.importId,
                        preview.getFoundItems(), preview.getReadyItems(), preview.getSkippedItems(),
                        preview.getAlreadyTracked(), preview.getNewItems(), preview.getAbsentTracked(),
                        preview.getExcludedByLimit(), preview.getFreeSlots(), preview.getAddableItems(),
                        preview.getSyncTargetItems(), preview.getAbsentTitles(),
                        preview.getExcludedByLimitTitles(), preview.getDestructivePlanFingerprint()
                )));
        context.handler.handleCallback(new IncomingTelegramCallback(
                "callback", context.telegramUserId, context.telegramUserId, "private",
                context.codec.encode(SharedBasketCallbackCodec.Action.BACK, context.importId, context.telegramUserId)
        ));
        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway).sendMessage(message.capture());
        return message.getValue();
    }

    private TestContext context() {
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
        return new TestContext(
                now, userId, importId, telegramUserId, service, gateway, codec,
                new TelegramSharedBasketHandler(
                        new SharedBasketUrlParser(), service, codec, users, gateway,
                        Clock.fixed(now, ZoneOffset.UTC)
                )
        );
    }

    private static final class TestContext {
        private final Instant now;
        private final UUID userId;
        private final UUID importId;
        private final long telegramUserId;
        private final SharedBasketImportService service;
        private final TelegramGateway gateway;
        private final SharedBasketCallbackCodec codec;
        private final TelegramSharedBasketHandler handler;

        private TestContext(
                Instant now,
                UUID userId,
                UUID importId,
                long telegramUserId,
                SharedBasketImportService service,
                TelegramGateway gateway,
                SharedBasketCallbackCodec codec,
                TelegramSharedBasketHandler handler
        ) {
            this.now = now;
            this.userId = userId;
            this.importId = importId;
            this.telegramUserId = telegramUserId;
            this.service = service;
            this.gateway = gateway;
            this.codec = codec;
            this.handler = handler;
        }
    }
}
