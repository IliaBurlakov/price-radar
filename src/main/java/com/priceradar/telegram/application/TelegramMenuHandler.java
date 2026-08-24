package com.priceradar.telegram.application;

import java.util.Optional;

public final class TelegramMenuHandler {

    private final TelegramMenuMessageFactory messageFactory;
    private final TrackedItemsMessageHandler trackedItemsHandler;
    private final TelegramGateway telegramGateway;

    public TelegramMenuHandler(
            TelegramMenuMessageFactory messageFactory,
            TrackedItemsMessageHandler trackedItemsHandler,
            TelegramGateway telegramGateway
    ) {
        if (messageFactory == null || trackedItemsHandler == null || telegramGateway == null) {
            throw new IllegalArgumentException("menu handler dependencies must not be null");
        }
        this.messageFactory = messageFactory;
        this.trackedItemsHandler = trackedItemsHandler;
        this.telegramGateway = telegramGateway;
    }

    public boolean handleMessage(IncomingTelegramMessage message) {
        if (!message.isPrivateChat()) {
            return false;
        }
        String text = message.getText();
        if (isCommand(text, "/start")) {
            telegramGateway.sendMessage(messageFactory.welcome(message.getChatId()));
            return true;
        }
        if (isCommand(text, "/help")) {
            telegramGateway.sendMessage(messageFactory.help(message.getChatId()));
            return true;
        }
        if (isCommand(text, "/menu")) {
            telegramGateway.sendMessage(messageFactory.mainMenu(message.getChatId()));
            return true;
        }
        return false;
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        Optional<MainMenuCallbackData.Action> action = MainMenuCallbackData.parse(
                callback.getData()
        );
        if (action.isEmpty()) {
            return false;
        }
        if (!callback.isPrivateChat()) {
            return true;
        }
        switch (action.orElseThrow()) {
            case HOME -> telegramGateway.sendMessage(
                    messageFactory.mainMenu(callback.getChatId())
            );
            case ADD_PRODUCT -> telegramGateway.sendMessage(
                    messageFactory.addProduct(callback.getChatId())
            );
            case TRACKED_ITEMS -> trackedItemsHandler.showTracked(
                    callback.getTelegramUserId(),
                    callback.getChatId()
            );
            case HELP -> telegramGateway.sendMessage(
                    messageFactory.help(callback.getChatId())
            );
        }
        return true;
    }

    private boolean isCommand(String text, String command) {
        return text.equals(command) || text.startsWith(command + "@");
    }

}
