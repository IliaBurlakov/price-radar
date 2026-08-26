package com.priceradar.telegram.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelegramUpdateDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramUpdateDispatcher.class);

    private final TelegramCurrentQuoteHandler currentQuoteHandler;
    private final TelegramMenuHandler menuHandler;
    private final TelegramTrackingHandler trackingHandler;
    private final TelegramSharedBasketHandler sharedBasketHandler;
    private final TrackedItemsMessageHandler trackedItemsHandler;
    private final ShowLastKnownCallbackHandler showLastKnownHandler;
    private final StatisticsCallbackHandler statisticsHandler;
    private final TelegramGateway telegramGateway;

    public TelegramUpdateDispatcher(
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramMenuHandler menuHandler,
            TelegramTrackingHandler trackingHandler,
            TelegramSharedBasketHandler sharedBasketHandler,
            TrackedItemsMessageHandler trackedItemsHandler,
            ShowLastKnownCallbackHandler showLastKnownHandler,
            StatisticsCallbackHandler statisticsHandler,
            TelegramGateway telegramGateway
    ) {
        if (currentQuoteHandler == null || menuHandler == null || trackingHandler == null
                || sharedBasketHandler == null
                || trackedItemsHandler == null
                || showLastKnownHandler == null || statisticsHandler == null
                || telegramGateway == null) {
            throw new IllegalArgumentException("Telegram update dispatcher dependencies must not be null");
        }
        this.currentQuoteHandler = currentQuoteHandler;
        this.menuHandler = menuHandler;
        this.trackingHandler = trackingHandler;
        this.sharedBasketHandler = sharedBasketHandler;
        this.trackedItemsHandler = trackedItemsHandler;
        this.showLastKnownHandler = showLastKnownHandler;
        this.statisticsHandler = statisticsHandler;
        this.telegramGateway = telegramGateway;
    }

    public void dispatch(TelegramUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("update must not be null");
        }
        if (update.getCallback().isPresent()) {
            dispatchCallback(update.getCallback().orElseThrow());
            return;
        }
        update.getMessage().ifPresent(message -> {
            if (trackingHandler.handleTargetPriceInput(message)) {
                return;
            }
            if (sharedBasketHandler.handleMessage(message)) {
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

    private void dispatchCallback(IncomingTelegramCallback callback) {
        try {
            if (sharedBasketHandler.handleCallback(callback)) {
                return;
            }
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
        } finally {
            answerCallbackBestEffort(callback.getCallbackQueryId());
        }
    }

    private void answerCallbackBestEffort(String callbackQueryId) {
        try {
            telegramGateway.answerCallbackQuery(callbackQueryId);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not acknowledge Telegram callback, errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
