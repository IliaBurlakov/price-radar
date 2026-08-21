package com.priceradar.telegram.application;

public class TelegramUpdateDispatcher {

    private final TelegramCurrentQuoteHandler currentQuoteHandler;
    private final TelegramMenuHandler menuHandler;
    private final TelegramTrackingHandler trackingHandler;
    private final TrackedItemsMessageHandler trackedItemsHandler;
    private final ShowLastKnownCallbackHandler showLastKnownHandler;
    private final StatisticsCallbackHandler statisticsHandler;

    public TelegramUpdateDispatcher(
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramMenuHandler menuHandler,
            TelegramTrackingHandler trackingHandler,
            TrackedItemsMessageHandler trackedItemsHandler,
            ShowLastKnownCallbackHandler showLastKnownHandler,
            StatisticsCallbackHandler statisticsHandler
    ) {
        if (currentQuoteHandler == null || menuHandler == null || trackingHandler == null
                || trackedItemsHandler == null
                || showLastKnownHandler == null || statisticsHandler == null) {
            throw new IllegalArgumentException("Telegram update dispatcher dependencies must not be null");
        }
        this.currentQuoteHandler = currentQuoteHandler;
        this.menuHandler = menuHandler;
        this.trackingHandler = trackingHandler;
        this.trackedItemsHandler = trackedItemsHandler;
        this.showLastKnownHandler = showLastKnownHandler;
        this.statisticsHandler = statisticsHandler;
    }

    public void dispatch(TelegramUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("update must not be null");
        }
        if (update.getCallback().isPresent()) {
            IncomingTelegramCallback callback = update.getCallback().orElseThrow();
            if (menuHandler.handleCallback(callback)) {
                return;
            }
            if (showLastKnownHandler.handleCallback(callback)) {
                return;
            }
            if (statisticsHandler.handleCallback(callback)) {
                return;
            }
            if (trackedItemsHandler.handleCallback(callback)) {
                return;
            }
            trackingHandler.handleCallback(callback);
            return;
        }
        update.getMessage().ifPresent(message -> {
            if (trackingHandler.handleTargetPriceInput(message)) {
                return;
            }
            if (menuHandler.handleMessage(message)) {
                return;
            }
            if (trackedItemsHandler.handleMessage(message)) {
                return;
            }
            currentQuoteHandler.handle(message);
        });
    }
}
