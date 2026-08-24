package com.priceradar.telegram.application;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramUpdateDispatcherTest {

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
    }
}
