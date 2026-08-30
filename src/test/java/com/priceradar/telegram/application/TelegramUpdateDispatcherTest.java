package com.priceradar.telegram.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelegramUpdateDispatcherTest {

    @Test
    void onboardingGateStopsCommandsAndUrlsBeforeBusinessHandlers() {
        TelegramOnboardingHandler onboarding = mock(TelegramOnboardingHandler.class);
        TelegramCurrentQuoteHandler quotes = mock(TelegramCurrentQuoteHandler.class);
        TelegramMenuHandler menu = mock(TelegramMenuHandler.class);
        TelegramTrackingHandler tracking = mock(TelegramTrackingHandler.class);
        TelegramSharedBasketHandler baskets = mock(TelegramSharedBasketHandler.class);
        TrackedItemsMessageHandler tracked = mock(TrackedItemsMessageHandler.class);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                onboarding,
                quotes,
                menu,
                tracking,
                baskets,
                tracked,
                mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class),
                mock(TelegramGateway.class)
        );

        for (String text : List.of(
                "/start",
                "/add",
                "/import",
                "https://www.wildberries.ru/catalog/123456/detail.aspx"
        )) {
            IncomingTelegramMessage message = message(text);
            when(onboarding.handleMessage(message)).thenReturn(true);

            dispatcher.dispatch(new TelegramUpdate(1L, Optional.of(message)));
        }

        verifyNoInteractions(quotes, menu, tracking, baskets, tracked);
    }

    @Test
    void onboardingGateStopsOldCallbackButStillAcknowledgesIt() {
        TelegramOnboardingHandler onboarding = mock(TelegramOnboardingHandler.class);
        TelegramMenuHandler menu = mock(TelegramMenuHandler.class);
        TelegramSharedBasketHandler baskets = mock(TelegramSharedBasketHandler.class);
        TelegramTrackingHandler tracking = mock(TelegramTrackingHandler.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        IncomingTelegramCallback callback = new IncomingTelegramCallback(
                "old-callback",
                7001L,
                7001L,
                "private",
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.TRACKED_ITEMS)
        );
        when(onboarding.handleCallback(callback)).thenReturn(true);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                onboarding,
                mock(TelegramCurrentQuoteHandler.class),
                menu,
                tracking,
                baskets,
                mock(TrackedItemsMessageHandler.class),
                mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class),
                gateway
        );

        dispatcher.dispatch(new TelegramUpdate(
                1L, Optional.empty(), Optional.of(callback)
        ));

        verify(onboarding).handleCallback(callback);
        verify(menu, never()).handleCallback(callback);
        verify(baskets, never()).handleCallback(callback);
        verify(tracking, never()).handleCallback(callback);
        verify(gateway).answerCallbackQuery("old-callback");
    }

    @Test
    void backAndMainMenuNeverDuplicateTheSameDestination() {
        TelegramMenuMessageFactory messages = new TelegramMenuMessageFactory();
        assertThat(messages.addProduct(7001L).getInlineKeyboard())
                .flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .containsExactly("Главное меню");
        assertThat(messages.importBasket(7001L).getInlineKeyboard())
                .flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .containsExactly("Главное меню");
        assertThat(messages.help(7001L).getInlineKeyboard())
                .flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .containsExactly("Главное меню");

        List<TelegramInlineButton> itemNavigation = TelegramNavigationKeyboard
                .itemSubscreen(java.util.UUID.randomUUID())
                .stream()
                .flatMap(List::stream)
                .toList();
        String backDestination = itemNavigation.stream()
                .filter(button -> button.getText().equals("← Назад"))
                .findFirst()
                .orElseThrow()
                .getCallbackData();
        String homeDestination = itemNavigation.stream()
                .filter(button -> button.getText().equals("Главное меню"))
                .findFirst()
                .orElseThrow()
                .getCallbackData();
        assertThat(backDestination).isNotEqualTo(homeDestination);
    }

    @Test
    void globalCommandsReuseExistingMenuAndTrackedItemsFlows() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        TrackedItemsMessageHandler trackedItems = mock(TrackedItemsMessageHandler.class);
        TelegramMenuMessageFactory messages = new TelegramMenuMessageFactory();
        TelegramMenuHandler handler = new TelegramMenuHandler(
                messages, trackedItems, mock(TelegramRegionHandler.class), gateway
        );

        assertSentMessage(
                gateway,
                () -> handler.handleMessage(message("/start@PriceRadarBot")),
                messages.welcome(7001L)
        );
        assertSentMessage(
                gateway,
                () -> handler.handleMessage(message("/add")),
                messages.addProduct(7001L)
        );
        assertSentMessage(
                gateway,
                () -> handler.handleMessage(message("/import")),
                messages.importBasket(7001L)
        );
        assertSentMessage(
                gateway,
                () -> handler.handleMessage(message("/help")),
                messages.help(7001L)
        );

        assertThat(handler.handleMessage(message("/tracked"))).isTrue();
        verify(trackedItems).showTracked(7001L, 7001L);
    }

    @Test
    void unknownCommandGetsHintAndUnsupportedTextShowsTheFullHelpMessage() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramMenuMessageFactory messages = new TelegramMenuMessageFactory();
        TelegramMenuHandler menuHandler = new TelegramMenuHandler(
                messages, mock(TrackedItemsMessageHandler.class),
                mock(TelegramRegionHandler.class), gateway
        );

        assertSentMessage(
                gateway,
                () -> menuHandler.handleMessage(message("/abracadabra")),
                messages.unknownCommand(7001L)
        );

        TelegramCurrentQuoteHandler currentQuoteHandler = mock(TelegramCurrentQuoteHandler.class);
        TelegramSharedBasketHandler sharedBasketHandler = mock(TelegramSharedBasketHandler.class);
        TelegramUpdateDispatcher dispatcher = dispatcher(
                currentQuoteHandler, menuHandler, sharedBasketHandler, gateway
        );
        IncomingTelegramMessage productUrl = message(
                "https://www.wildberries.ru/catalog/10302970/detail.aspx"
        );
        when(currentQuoteHandler.handle(productUrl)).thenReturn(true);

        dispatcher.dispatch(new TelegramUpdate(3L, Optional.of(productUrl)));

        verify(currentQuoteHandler).handle(productUrl);

        IncomingTelegramMessage unsupportedText = message("Какой-то случайный текст");
        assertSentMessage(
                gateway,
                () -> dispatcher.dispatch(new TelegramUpdate(4L, Optional.of(unsupportedText))),
                messages.help(7001L)
        );
        verify(currentQuoteHandler).handle(unsupportedText);
    }

    @Test
    void mainNavigationExplainsBasketImportAndDispatcherAcceptsTheFollowingSharedLink() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramMenuMessageFactory messages = new TelegramMenuMessageFactory();
        TelegramMenuHandler menuHandler = new TelegramMenuHandler(
                messages, mock(TrackedItemsMessageHandler.class),
                mock(TelegramRegionHandler.class), gateway
        );
        OutgoingTelegramMessage mainMenu = messages.mainMenu(7001L);

        assertThat(mainMenu.getInlineKeyboard().stream()
                .flatMap(List::stream)
                .map(TelegramInlineButton::getText)
                .toList()).contains("Добавить товар", "Импортировать корзину");

        menuHandler.handleCallback(new IncomingTelegramCallback(
                "basket-help", 7001L, 7001L, "private",
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.IMPORT_BASKET)
        ));
        var explanation = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(explanation.capture());
        assertThat(explanation.getValue().getText()).contains(
                "Импорт корзины Wildberries",
                "Поделиться корзиной",
                "Добавить новые",
                "Синхронизировать",
                "не более 50 товаров",
                "Отправьте ссылку на общую корзину"
        );
        assertThat(explanation.getValue().getInlineKeyboard().stream()
                .flatMap(List::stream)
                .map(TelegramInlineButton::getText)
                .toList()).containsExactly("Главное меню");

        TelegramSharedBasketHandler sharedBasketHandler = mock(TelegramSharedBasketHandler.class);
        IncomingTelegramMessage sharedLink = new IncomingTelegramMessage(
                7001L, 7001L, "private",
                "https://www.wildberries.ru/basket?shareId=abc123def4"
        );
        when(sharedBasketHandler.handleMessage(sharedLink)).thenReturn(true);
        TelegramUpdateDispatcher dispatcher = dispatcher(
                mock(TelegramCurrentQuoteHandler.class), menuHandler, sharedBasketHandler, gateway
        );

        dispatcher.dispatch(new TelegramUpdate(2L, Optional.of(sharedLink)));

        verify(sharedBasketHandler).handleMessage(sharedLink);
    }

    @Test
    void acknowledgesHandledCallbackExactlyOnceAtTheDispatchBoundary() {
        TelegramCurrentQuoteHandler currentQuoteHandler = mock(TelegramCurrentQuoteHandler.class);
        TelegramMenuHandler menuHandler = mock(TelegramMenuHandler.class);
        TelegramTrackingHandler trackingHandler = mock(TelegramTrackingHandler.class);
        TelegramSharedBasketHandler sharedBasketHandler = mock(TelegramSharedBasketHandler.class);
        TrackedItemsMessageHandler trackedItemsHandler = mock(TrackedItemsMessageHandler.class);
        ShowLastKnownCallbackHandler showLastKnownHandler = mock(ShowLastKnownCallbackHandler.class);
        StatisticsCallbackHandler statisticsHandler = mock(StatisticsCallbackHandler.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                mock(TelegramOnboardingHandler.class),
                currentQuoteHandler,
                menuHandler,
                trackingHandler,
                sharedBasketHandler,
                trackedItemsHandler,
                showLastKnownHandler,
                statisticsHandler,
                gateway
        );
        IncomingTelegramCallback callback = new IncomingTelegramCallback(
                "callback-1",
                7001L,
                7001L,
                "private",
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.HOME)
        );
        when(menuHandler.handleCallback(callback)).thenReturn(true);

        dispatcher.dispatch(new TelegramUpdate(
                1L,
                Optional.empty(),
                Optional.of(callback)
        ));

        verify(menuHandler).handleCallback(callback);
        verify(gateway).answerCallbackQuery("callback-1");
        verify(trackingHandler).clearPendingInput(7001L, 7001L);
    }

    @Test
    void commandNavigationClearsOldPendingTargetInput() {
        TelegramTrackingHandler trackingHandler = mock(TelegramTrackingHandler.class);
        TelegramMenuHandler menuHandler = mock(TelegramMenuHandler.class);
        IncomingTelegramMessage command = message("/help");
        when(menuHandler.handleMessage(command)).thenReturn(true);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                mock(TelegramOnboardingHandler.class),
                mock(TelegramCurrentQuoteHandler.class),
                menuHandler,
                trackingHandler,
                mock(TelegramSharedBasketHandler.class),
                mock(TrackedItemsMessageHandler.class),
                mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class),
                mock(TelegramGateway.class)
        );

        dispatcher.dispatch(new TelegramUpdate(5L, Optional.of(command)));

        verify(trackingHandler).clearPendingInput(7001L, 7001L);
        verify(trackingHandler, never()).handleTargetPriceInput(command);
    }

    private void assertSentMessage(
            TelegramGateway gateway,
            Runnable action,
            OutgoingTelegramMessage expected
    ) {
        clearInvocations(gateway);
        action.run();
        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue()).usingRecursiveComparison().isEqualTo(expected);
    }

    private IncomingTelegramMessage message(String text) {
        return new IncomingTelegramMessage(7001L, 7001L, "private", text);
    }

    private TelegramUpdateDispatcher dispatcher(
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramMenuHandler menuHandler,
            TelegramSharedBasketHandler sharedBasketHandler,
            TelegramGateway gateway
    ) {
        return new TelegramUpdateDispatcher(
                mock(TelegramOnboardingHandler.class),
                currentQuoteHandler,
                menuHandler,
                mock(TelegramTrackingHandler.class),
                sharedBasketHandler,
                mock(TrackedItemsMessageHandler.class),
                mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class),
                gateway
        );
    }
}
