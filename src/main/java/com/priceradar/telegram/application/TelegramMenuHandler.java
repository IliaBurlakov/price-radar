package com.priceradar.telegram.application;

import java.util.Optional;

public final class TelegramMenuHandler {

    private final TelegramMenuMessageFactory messageFactory;
    private final TrackedItemsMessageHandler trackedItemsHandler;
    private final TelegramRegionHandler regionHandler;
    private final TelegramGateway telegramGateway;

    public TelegramMenuHandler(
            TelegramMenuMessageFactory messageFactory,
            TrackedItemsMessageHandler trackedItemsHandler,
            TelegramRegionHandler regionHandler,
            TelegramGateway telegramGateway
    ) {
        if (messageFactory == null || trackedItemsHandler == null
                || regionHandler == null || telegramGateway == null) {
            throw new IllegalArgumentException("menu handler dependencies must not be null");
        }
        this.messageFactory = messageFactory;
        this.trackedItemsHandler = trackedItemsHandler;
        this.regionHandler = regionHandler;
        this.telegramGateway = telegramGateway;
    }

    public boolean handleMessage(IncomingTelegramMessage message) {
        if (!message.isPrivateChat()) {
            return false;
        }
        String text = message.getText();
        Optional<TelegramBotCommand> command = TelegramBotCommand.fromMessageText(text);
        if (command.isPresent()) {
            handleCommand(command.orElseThrow(), message);
            return true;
        }
        if (isLegacyMenuCommand(text)) {
            telegramGateway.sendMessage(messageFactory.mainMenu(message.getChatId()));
            return true;
        }
        if (TelegramBotCommand.isCommandText(text)) {
            telegramGateway.sendMessage(messageFactory.unknownCommand(message.getChatId()));
            return true;
        }
        return false;
    }

    private void handleCommand(
            TelegramBotCommand command,
            IncomingTelegramMessage message
    ) {
        switch (command) {
            case START -> telegramGateway.sendMessage(messageFactory.welcome(message.getChatId()));
            case TRACKED -> trackedItemsHandler.showTracked(
                    message.getTelegramUserId(),
                    message.getChatId()
            );
            case ADD -> telegramGateway.sendMessage(messageFactory.addProduct(message.getChatId()));
            case IMPORT -> telegramGateway.sendMessage(messageFactory.importBasket(message.getChatId()));
            case HELP -> telegramGateway.sendMessage(messageFactory.help(message.getChatId()));
        }
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        if (regionHandler.handleCallback(callback)) {
            return true;
        }
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
            case IMPORT_BASKET -> telegramGateway.sendMessage(
                    messageFactory.importBasket(callback.getChatId())
            );
            case TRACKED_ITEMS -> trackedItemsHandler.showTracked(
                    callback.getTelegramUserId(),
                    callback.getChatId()
            );
            case REGION -> regionHandler.show(callback.getTelegramUserId(), callback.getChatId());
            case HELP -> telegramGateway.sendMessage(
                    messageFactory.help(callback.getChatId())
            );
        }
        return true;
    }

    public boolean showHelpForUnsupportedText(IncomingTelegramMessage message) {
        if (!message.isPrivateChat()) {
            return false;
        }
        telegramGateway.sendMessage(messageFactory.help(message.getChatId()));
        return true;
    }

    private boolean isLegacyMenuCommand(String text) {
        return text.equals("/menu") || text.startsWith("/menu@");
    }

}
