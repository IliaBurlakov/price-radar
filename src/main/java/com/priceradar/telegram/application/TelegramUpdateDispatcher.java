package com.priceradar.telegram.application;

public class TelegramUpdateDispatcher {

    private final TelegramCurrentQuoteHandler currentQuoteHandler;
    private final TelegramTrackingHandler trackingHandler;

    public TelegramUpdateDispatcher(
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramTrackingHandler trackingHandler
    ) {
        if (currentQuoteHandler == null || trackingHandler == null) {
            throw new IllegalArgumentException("Telegram update dispatcher dependencies must not be null");
        }
        this.currentQuoteHandler = currentQuoteHandler;
        this.trackingHandler = trackingHandler;
    }

    public void dispatch(TelegramUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("update must not be null");
        }
        if (update.getCallback().isPresent()) {
            trackingHandler.handleCallback(update.getCallback().orElseThrow());
            return;
        }
        update.getMessage().ifPresent(message -> {
            if (!trackingHandler.handleTargetPriceInput(message)) {
                currentQuoteHandler.handle(message);
            }
        });
    }
}
