package com.priceradar.telegram.application;

import com.priceradar.region.domain.MarketplaceRegionCode;
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
import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;

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
                moscow(), UserPricePreferences.defaults()
        );
        SharedBasketPreview preview = new SharedBasketPreview(
                importId, MarketplaceRegionCode.MOSCOW,
                60, 60, 60, 0, List.of(), 15, 45, 8, 2, 27, 27, 50,
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
        TelegramInlineButton back = message.getValue().getInlineKeyboard().stream()
                .flatMap(List::stream)
                .filter(button -> button.getText().equals("← Назад"))
                .findFirst()
                .orElseThrow();
        assertThat(codec.decode(back.getCallbackData(), telegramUserId))
                .get()
                .extracting(SharedBasketCallbackCodec.Decoded::getAction)
                .isEqualTo(SharedBasketCallbackCodec.Action.BACK);
        verify(service, never()).apply(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );

        org.mockito.Mockito.clearInvocations(gateway);
        handler.handleCallback(new IncomingTelegramCallback(
                "back", telegramUserId, telegramUserId, "private", back.getCallbackData()
        ));

        var previewMessage = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(previewMessage.capture());
        assertThat(previewMessage.getValue().getText())
                .startsWith("🛒 Корзина Wildberries")
                .contains("Найдено товаров: 60");
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
                moscow(), UserPricePreferences.defaults()
        ));
        when(service.findPreview(importId, userId, now)).thenReturn(Optional.empty());
        TelegramSharedBasketHandler handler = new TelegramSharedBasketHandler(
                new SharedBasketUrlParser(), service, codec, users, gateway,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        handler.handleCallback(new IncomingTelegramCallback(
                "callback", telegramUserId, telegramUserId, "private",
                codec.encode(SharedBasketCallbackCodec.Action.BACK, importId, telegramUserId)
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
    void oldBasketCallbackFromPreviousRegionCannotApplyImport() {
        Instant now = Instant.parse("2026-08-24T10:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        long telegramUserId = 7001L;
        SharedBasketImportService service = mock(SharedBasketImportService.class);
        UserProfileService users = mock(UserProfileService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        SharedBasketCallbackCodec codec = new SharedBasketCallbackCodec(
                "01234567890123456789012345678901"
        );
        when(users.getOrCreate(telegramUserId, telegramUserId)).thenReturn(new UserProfile(
                userId, telegramUserId, telegramUserId,
                com.priceradar.testsupport.TestMarketplaceRegions.irkutsk(),
                UserPricePreferences.defaults()
        ));
        when(service.findPreview(importId, userId, now)).thenReturn(Optional.of(
                new SharedBasketPreview(
                        importId, MarketplaceRegionCode.MOSCOW,
                        1, 1, 1, 0, List.of(), 0, 1, 0, 0, 50, 1, 1,
                        List.of(), List.of(), "AbCdEf12345"
                )
        ));
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
        assertThat(message.getValue().getText()).contains(
                "импорт относится к предыдущему региону", "Отправьте ссылку"
        );
        verify(service, never()).apply(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
    }


    @Test
    void unresolvedItemsHideSynchronizationAndSignedSyncCallbackCannotApplyIt() {
        TestContext context = context();
        SharedBasketPreview preview = new SharedBasketPreview(
                context.importId, MarketplaceRegionCode.MOSCOW,
                3, 2, 2, 1, List.of(), 0, 2, 0, 0, 48, 2, 2,
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
                "Ещё 1 товар не удалось обработать.",
                "Попробуйте повторить импорт позже.",
                "Синхронизация сейчас недоступна."
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
        assertThat(message.getValue().getText()).contains("Синхронизация сейчас недоступна");
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
                context.importId, MarketplaceRegionCode.MOSCOW,
                1, 1, 1, 0, List.of(), 0, 1, 1, 0, 49, 1, 1,
                List.of("Old removal"), List.of(), "AbCdEf12345"
        );
        SharedBasketPreview refreshed = new SharedBasketPreview(
                context.importId, MarketplaceRegionCode.MOSCOW,
                1, 1, 1, 0, List.of(), 0, 1, 2, 0, 48, 1, 1,
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
                UUID.randomUUID(), MarketplaceRegionCode.MOSCOW,
                3, 3, 3, 0, List.of(), 0, 3, 0, 0, 50, 3, 3,
                List.of(), List.of(), "AbCdEf12345"
        );
        String happyText = renderPreview(happyPath).getText();

        assertThat(happyText)
                .contains("Найдено товаров: 3", "Новых: 3")
                .doesNotContain(
                        "Готово к импорту",
                        "Уже отслеживаются: 0",
                        "не удалось обработать",
                        "Сейчас недоступ",
                        "Лимит отслеживания",
                        "При синхронизации бот перестанет"
                );

        SharedBasketPreview warnings = new SharedBasketPreview(
                UUID.randomUUID(), MarketplaceRegionCode.MOSCOW,
                73, 70, 70, 1, List.of("Product A", "Product B"),
                15, 55, 6, 2, 35, 35, 50,
                List.of("Outside"), List.of("Limited"), "ZyXwVu98765"
        );
        String warningText = renderPreview(warnings).getText();

        assertThat(warningText).contains(
                "Уже отслеживаются: 15",
                "При синхронизации бот перестанет отслеживать",
                "6 товаров, которых нет в этой корзине",
                "Сейчас недоступны 2 товара:",
                "• Product A",
                "• Product B",
                "Они не будут добавлены.",
                "Ещё 1 товар не удалось обработать.",
                "Лимит отслеживания — 50 товаров",
                "20 товаров из этой корзины не войдут в список"
        ).doesNotContain("Готово к импорту", ": 0");
    }

    @Test
    void unavailableBlockUsesSingularFormAndLimitsLongProductLists() {
        SharedBasketPreview twoUnavailable = new SharedBasketPreview(
                UUID.randomUUID(), MarketplaceRegionCode.MOSCOW,
                28, 26, 26, 0, List.of("Product A", "Product B"),
                0, 26, 0, 0, 50, 26, 26,
                List.of(), List.of(), "QrStUv12345"
        );
        assertThat(renderPreview(twoUnavailable).getText()).contains(
                "Найдено товаров: 28",
                "Новых: 26",
                "⚠️ Сейчас недоступны 2 товара:\n\n• Product A\n• Product B",
                "Они не будут добавлены."
        );

        SharedBasketPreview singular = new SharedBasketPreview(
                UUID.randomUUID(), MarketplaceRegionCode.MOSCOW,
                1, 0, 0, 0, List.of("Футболка — XXL"),
                0, 0, 0, 0, 50, 0, 0,
                List.of(), List.of(), "AbCdEf12345"
        );
        OutgoingTelegramMessage singularMessage = renderPreview(singular);
        assertThat(singularMessage.getText()).contains(
                "⚠️ Сейчас недоступен 1 товар:",
                "• Футболка — XXL",
                "Он не будет добавлен."
        );
        assertThat(singularMessage.getInlineKeyboard().stream()
                .flatMap(List::stream)
                .map(TelegramInlineButton::getText)
                .toList())
                .containsExactly("Добавить новые", "Отмена");

        SharedBasketPreview longList = new SharedBasketPreview(
                UUID.randomUUID(), MarketplaceRegionCode.MOSCOW,
                8, 0, 0, 0,
                List.of("A", "B", "C", "D", "E", "F", "G", "H"),
                0, 0, 0, 0, 50, 0, 0,
                List.of(), List.of(), "ZyXwVu98765"
        );
        assertThat(renderPreview(longList).getText())
                .contains(
                        "⚠️ Сейчас недоступны 8 товаров:",
                        "• A", "• B", "• C", "• D", "• E", "• и ещё 3",
                        "Они не будут добавлены."
                )
                .doesNotContain("• F", "• G", "• H");
    }

    private OutgoingTelegramMessage renderPreview(SharedBasketPreview preview) {
        TestContext context = context();
        when(context.service.findPreview(context.importId, context.userId, context.now))
                .thenReturn(Optional.of(new SharedBasketPreview(
                        context.importId,
                        MarketplaceRegionCode.MOSCOW,
                        preview.getFoundItems(), preview.getAvailableItems(), preview.getReadyItems(),
                        preview.getUnresolvedItems(), preview.getUnavailableTitles(),
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
                moscow(), UserPricePreferences.defaults()
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
