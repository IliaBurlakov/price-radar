package com.priceradar.telegram.application;

public class TelegramUpdateDispatcher {

    private final TelegramCurrentQuoteHandler currentQuoteHandler;
    private final TelegramTrackingHandler trackingHandler;
    private final TrackedItemsMessageHandler trackedItemsHandler;
    private final ShowLastKnownCallbackHandler showLastKnownHandler;

    public TelegramUpdateDispatcher(
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramTrackingHandler trackingHandler,
            TrackedItemsMessageHandler trackedItemsHandler,
            ShowLastKnownCallbackHandler showLastKnownHandler
    ) {
        if (currentQuoteHandler == null || trackingHandler == null || trackedItemsHandler == null
                || showLastKnownHandler == null) {
            throw new IllegalArgumentException("Telegram update dispatcher dependencies must not be null");
        }
        this.currentQuoteHandler = currentQuoteHandler;
        this.trackingHandler = trackingHandler;
        this.trackedItemsHandler = trackedItemsHandler;
        this.showLastKnownHandler = showLastKnownHandler;
    }

    public void dispatch(TelegramUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("update must not be null");
        }
        if (update.getCallback().isPresent()) {
            IncomingTelegramCallback callback = update.getCallback().orElseThrow();
            if (showLastKnownHandler.handleCallback(callback)) {
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
            if (trackedItemsHandler.handleMessage(message)) {
                return;
            }
            currentQuoteHandler.handle(message);
        });
    }
}
