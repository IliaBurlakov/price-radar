package com.priceradar.telegram.application;

import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelegramUpdateDispatcherTest {

    @Test
    void pendingFeedbackConsumesTextBeforeOnboardingAndBusinessHandlers() {
        TelegramFeedbackHandler feedback = mock(TelegramFeedbackHandler.class);
        TelegramOnboardingHandler onboarding = mock(TelegramOnboardingHandler.class);
        TelegramCurrentQuoteHandler quotes = mock(TelegramCurrentQuoteHandler.class);
        IncomingTelegramMessage message = message("https://www.wildberries.ru/catalog/123/detail.aspx");
        when(feedback.handlePendingMessage(message)).thenReturn(true);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                onboarding, quotes, mock(TelegramMenuHandler.class), mock(TelegramRegionHandler.class),
                mock(TelegramTrackingHandler.class), mock(TelegramSharedBasketHandler.class),
                mock(TrackedItemsMessageHandler.class), mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class), feedback, mock(TelegramGateway.class));

        dispatcher.dispatch(new TelegramUpdate(1L, Optional.of(message)));

        verify(feedback).handlePendingMessage(message);
        verifyNoInteractions(onboarding, quotes);
    }

    @Test
    void callbacksCancelPendingFeedbackExceptFeedbackEntryPoint() {
        TelegramFeedbackHandler feedback = mock(TelegramFeedbackHandler.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                mock(TelegramOnboardingHandler.class), mock(TelegramCurrentQuoteHandler.class),
                mock(TelegramMenuHandler.class), mock(TelegramRegionHandler.class),
                mock(TelegramTrackingHandler.class), mock(TelegramSharedBasketHandler.class),
                mock(TrackedItemsMessageHandler.class), mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class), feedback, gateway);
        IncomingTelegramCallback home = new IncomingTelegramCallback(
                "home", 7001L, 7001L, "private",
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.HOME));
        IncomingTelegramCallback openFeedback = new IncomingTelegramCallback(
                "feedback", 7001L, 7001L, "private",
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.FEEDBACK));
        when(feedback.supportsCallback(openFeedback)).thenReturn(true);
        when(feedback.handleCallback(openFeedback)).thenReturn(true);

        dispatcher.dispatch(new TelegramUpdate(1L, Optional.empty(), Optional.of(home)));
        dispatcher.dispatch(new TelegramUpdate(2L, Optional.empty(), Optional.of(openFeedback)));

        verify(feedback).cancel(7001L, 7001L);
        verify(feedback).handleCallback(openFeedback);
        verify(gateway).answerCallbackQuery("home");
        verify(gateway).answerCallbackQuery("feedback");
    }

    @Test
    void onboardingGateStopsBusinessCommandsAndUrlsBeforeBusinessHandlers() {
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
                mock(TelegramRegionHandler.class),
                tracking,
                baskets,
                tracked,
                mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class),
                mock(TelegramFeedbackHandler.class),
                mock(TelegramGateway.class)
        );

        for (String text : List.of(
                "/add",
                "/import",
                "https://www.wildberries.ru/catalog/123456/detail.aspx",
                "Список:\nhttps://www.wildberries.ru/catalog/111/detail.aspx\n"
                        + "https://www.wildberries.ru/catalog/222/detail.aspx"
        )) {
            IncomingTelegramMessage message = message(text);
            when(onboarding.handleMessage(message)).thenReturn(true);

            dispatcher.dispatch(new TelegramUpdate(1L, Optional.of(message)));
        }

        verifyNoInteractions(quotes, menu, tracking, baskets, tracked);
    }

    @Test
    void startAndHelpReachOrdinaryMenuFlowWithoutSelectedCity() {
        UserProfileService users = mock(UserProfileService.class);
        TelegramRegionHandler regions = mock(TelegramRegionHandler.class);
        UserProfile unconfigured = new UserProfile(
                UUID.randomUUID(), 7001L, 7001L, null, UserPricePreferences.defaults()
        );
        UserProfile configured = new UserProfile(
                UUID.randomUUID(), 7001L, 7001L, moscow(), UserPricePreferences.defaults()
        );
        when(users.getOrCreate(7001L, 7001L))
                .thenReturn(unconfigured, configured, unconfigured);
        TelegramOnboardingHandler onboarding = new TelegramOnboardingHandler(users, regions);
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramMenuMessageFactory messages = new TelegramMenuMessageFactory(50);
        TelegramMenuHandler menu = new TelegramMenuHandler(
                messages, mock(TrackedItemsMessageHandler.class), regions, gateway
        );
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                onboarding, mock(TelegramCurrentQuoteHandler.class), menu, regions,
                mock(TelegramTrackingHandler.class), mock(TelegramSharedBasketHandler.class),
                mock(TrackedItemsMessageHandler.class), mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class), mock(TelegramFeedbackHandler.class), gateway
        );

        assertSentMessage(gateway, () -> dispatcher.dispatch(new TelegramUpdate(
                1L, Optional.of(message("/start"))
        )), messages.welcome(7001L));
        assertSentMessage(gateway, () -> dispatcher.dispatch(new TelegramUpdate(
                2L, Optional.of(message("/start"))
        )), messages.welcome(7001L));
        assertSentMessage(gateway, () -> dispatcher.dispatch(new TelegramUpdate(
                3L, Optional.of(message("/help"))
        )), messages.help(7001L));

        verify(regions, never()).showOnboarding(unconfigured);
    }

    @Test
    void sharedBasketKeepsRoutingPriorityWhenMessageAlsoContainsAProduct() {
        TelegramSharedBasketHandler baskets = mock(TelegramSharedBasketHandler.class);
        TelegramCurrentQuoteHandler quotes = mock(TelegramCurrentQuoteHandler.class);
        IncomingTelegramMessage message = message(
                "Моя корзина: https://www.wildberries.ru/basket?shareId=abc123def4\n"
                        + "Товар: https://www.wildberries.ru/catalog/123/detail.aspx"
        );
        when(baskets.handleMessage(message)).thenReturn(true);
        TelegramUpdateDispatcher dispatcher = dispatcher(
                quotes, mock(TelegramMenuHandler.class), baskets, mock(TelegramGateway.class)
        );

        dispatcher.dispatch(new TelegramUpdate(6L, Optional.of(message)));

        verify(baskets).handleMessage(message);
        verifyNoInteractions(quotes);
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
                mock(TelegramRegionHandler.class),
                tracking,
                baskets,
                mock(TrackedItemsMessageHandler.class),
                mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class),
                mock(TelegramFeedbackHandler.class),
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
        TelegramMenuMessageFactory messages = new TelegramMenuMessageFactory(50);
        assertThat(messages.addProduct(7001L).getText()).contains(
                "Отправьте одну или несколько ссылок", "в одном сообщении"
        );
        assertThat(messages.help(7001L).getText()).contains(
                "текст из Copy/Share", "список с несколькими ссылками", "Обратная связь"
        ).doesNotContain("ссылку на один товар");
        assertThat(TelegramNavigationKeyboard.mainMenu().getLast())
                .extracting(TelegramInlineButton::getText)
                .containsExactly("Помощь", "💬 Обратная связь");
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
        TelegramMenuMessageFactory messages = new TelegramMenuMessageFactory(50);
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
        TelegramMenuMessageFactory messages = new TelegramMenuMessageFactory(50);
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
        TelegramMenuMessageFactory messages = new TelegramMenuMessageFactory(50);
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
        TelegramRegionHandler regionHandler = mock(TelegramRegionHandler.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                mock(TelegramOnboardingHandler.class),
                currentQuoteHandler,
                menuHandler,
                regionHandler,
                trackingHandler,
                sharedBasketHandler,
                trackedItemsHandler,
                showLastKnownHandler,
                statisticsHandler,
                mock(TelegramFeedbackHandler.class),
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
        verify(regionHandler).clearPendingInput(7001L, 7001L);
    }

    @Test
    void openingRegionScreenDoesNotCancelItsOwnPendingSelection() {
        TelegramRegionHandler regionHandler = mock(TelegramRegionHandler.class);
        TelegramMenuHandler menuHandler = mock(TelegramMenuHandler.class);
        IncomingTelegramCallback callback = new IncomingTelegramCallback(
                "callback-region", 7001L, 7001L, "private",
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.REGION)
        );
        when(menuHandler.handleCallback(callback)).thenReturn(true);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                mock(TelegramOnboardingHandler.class),
                mock(TelegramCurrentQuoteHandler.class),
                menuHandler,
                regionHandler,
                mock(TelegramTrackingHandler.class),
                mock(TelegramSharedBasketHandler.class),
                mock(TrackedItemsMessageHandler.class),
                mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class),
                mock(TelegramFeedbackHandler.class),
                mock(TelegramGateway.class)
        );

        dispatcher.dispatch(new TelegramUpdate(1L, Optional.empty(), Optional.of(callback)));

        verify(regionHandler, never()).clearPendingInput(7001L, 7001L);
    }

    @Test
    void directRegionCallbackDoesNotCancelCitySelectionFlow() {
        TelegramRegionHandler regionHandler = mock(TelegramRegionHandler.class);
        IncomingTelegramCallback callback = new IncomingTelegramCallback(
                "callback-region-direct", 7001L, 7001L, "private", RegionCallbackData.OPEN
        );
        when(regionHandler.supportsCallback(callback)).thenReturn(true);
        TelegramMenuHandler menuHandler = mock(TelegramMenuHandler.class);
        when(menuHandler.handleCallback(callback)).thenReturn(true);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                mock(TelegramOnboardingHandler.class), mock(TelegramCurrentQuoteHandler.class),
                menuHandler, regionHandler, mock(TelegramTrackingHandler.class),
                mock(TelegramSharedBasketHandler.class), mock(TrackedItemsMessageHandler.class),
                mock(ShowLastKnownCallbackHandler.class), mock(StatisticsCallbackHandler.class),
                mock(TelegramFeedbackHandler.class),
                mock(TelegramGateway.class)
        );

        dispatcher.dispatch(new TelegramUpdate(1L, Optional.empty(), Optional.of(callback)));

        verify(regionHandler, never()).clearPendingInput(7001L, 7001L);
    }

    @Test
    void unrelatedTrackedCallbackCancelsCityFlowBeforeNextProductUrl() {
        TelegramRegionHandler regionHandler = mock(TelegramRegionHandler.class);
        TrackedItemsMessageHandler trackedItemsHandler = mock(TrackedItemsMessageHandler.class);
        TelegramCurrentQuoteHandler currentQuoteHandler = mock(TelegramCurrentQuoteHandler.class);
        IncomingTelegramCallback back = new IncomingTelegramCallback(
                "callback-back", 7001L, 7001L, "private",
                TrackedItemsPageCallbackData.encode(0)
        );
        when(trackedItemsHandler.handleCallback(back)).thenReturn(true);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                mock(TelegramOnboardingHandler.class), currentQuoteHandler,
                mock(TelegramMenuHandler.class), regionHandler,
                mock(TelegramTrackingHandler.class), mock(TelegramSharedBasketHandler.class),
                trackedItemsHandler, mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class), mock(TelegramFeedbackHandler.class),
                mock(TelegramGateway.class)
        );

        dispatcher.dispatch(new TelegramUpdate(1L, Optional.empty(), Optional.of(back)));
        IncomingTelegramMessage productUrl = message(
                "https://www.wildberries.ru/catalog/123/detail.aspx"
        );
        when(currentQuoteHandler.handle(productUrl)).thenReturn(true);
        dispatcher.dispatch(new TelegramUpdate(2L, Optional.of(productUrl)));

        verify(regionHandler).clearPendingInput(7001L, 7001L);
        verify(regionHandler).handleMessage(productUrl);
        verify(currentQuoteHandler).handle(productUrl);
    }

    @Test
    void commandNavigationClearsOldPendingTargetInput() {
        TelegramTrackingHandler trackingHandler = mock(TelegramTrackingHandler.class);
        TelegramRegionHandler regionHandler = mock(TelegramRegionHandler.class);
        TelegramMenuHandler menuHandler = mock(TelegramMenuHandler.class);
        IncomingTelegramMessage command = message("/help");
        when(menuHandler.handleMessage(command)).thenReturn(true);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(
                mock(TelegramOnboardingHandler.class),
                mock(TelegramCurrentQuoteHandler.class),
                menuHandler,
                regionHandler,
                trackingHandler,
                mock(TelegramSharedBasketHandler.class),
                mock(TrackedItemsMessageHandler.class),
                mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class),
                mock(TelegramFeedbackHandler.class),
                mock(TelegramGateway.class)
        );

        dispatcher.dispatch(new TelegramUpdate(5L, Optional.of(command)));

        verify(trackingHandler).clearPendingInput(7001L, 7001L);
        verify(regionHandler).clearPendingInput(7001L, 7001L);
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
                mock(TelegramRegionHandler.class),
                mock(TelegramTrackingHandler.class),
                sharedBasketHandler,
                mock(TrackedItemsMessageHandler.class),
                mock(ShowLastKnownCallbackHandler.class),
                mock(StatisticsCallbackHandler.class),
                mock(TelegramFeedbackHandler.class),
                gateway
        );
    }
}
