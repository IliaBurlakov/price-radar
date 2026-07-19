package com.priceradar.telegram.application;

public class TelegramUpdateDispatcher {

    private final TelegramCurrentQuoteHandler currentQuoteHandler;
    private final TelegramTrackingHandler trackingHandler;
    private final TrackedItemsMessageHandler trackedItemsHandler;

    public TelegramUpdateDispatcher(
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramTrackingHandler trackingHandler,
            TrackedItemsMessageHandler trackedItemsHandler
    ) {
        if (currentQuoteHandler == null || trackingHandler == null || trackedItemsHandler == null) {
            throw new IllegalArgumentException("Telegram update dispatcher dependencies must not be null");
        }
        this.currentQuoteHandler = currentQuoteHandler;
        this.trackingHandler = trackingHandler;
        this.trackedItemsHandler = trackedItemsHandler;
    }

    public void dispatch(TelegramUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("update must not be null");
        }
        if (update.getCallback().isPresent()) {
            IncomingTelegramCallback callback = update.getCallback().orElseThrow();
            if (!trackedItemsHandler.handleCallback(callback)) {
                trackingHandler.handleCallback(callback);
            }
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
