package com.priceradar.telegram.application;

import java.util.Optional;

import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;

public final class TelegramMenuHandler {

    private final TelegramMenuMessageFactory messageFactory;
    private final TrackedItemsMessageHandler trackedItemsHandler;
    private final TelegramRegionHandler regionHandler;
    private final TelegramFeedbackHandler feedbackHandler;
    private final TelegramWalletDiscountHandler walletDiscountHandler;
    private final UserProfileService userProfileService;
    private final SubscriptionService subscriptionService;
    private final TelegramGateway telegramGateway;

    public TelegramMenuHandler(
            TelegramMenuMessageFactory messageFactory,
            TrackedItemsMessageHandler trackedItemsHandler,
            TelegramRegionHandler regionHandler,
            TelegramFeedbackHandler feedbackHandler,
            TelegramWalletDiscountHandler walletDiscountHandler,
            UserProfileService userProfileService,
            SubscriptionService subscriptionService,
            TelegramGateway telegramGateway
    ) {
        if (messageFactory == null || trackedItemsHandler == null
                || regionHandler == null || feedbackHandler == null
                || walletDiscountHandler == null || userProfileService == null
                || subscriptionService == null || telegramGateway == null) {
            throw new IllegalArgumentException("menu handler dependencies must not be null");
        }
        this.messageFactory = messageFactory;
        this.trackedItemsHandler = trackedItemsHandler;
        this.regionHandler = regionHandler;
        this.feedbackHandler = feedbackHandler;
        this.walletDiscountHandler = walletDiscountHandler;
        this.userProfileService = userProfileService;
        this.subscriptionService = subscriptionService;
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
            case ADD -> telegramGateway.sendMessage(addProductMessage(message));
            case IMPORT -> telegramGateway.sendMessage(importBasketMessage(message));
            case CITY -> regionHandler.show(message.getTelegramUserId(), message.getChatId());
            case WALLET -> walletDiscountHandler.show(
                    message.getTelegramUserId(), message.getChatId()
            );
            case HELP -> telegramGateway.sendMessage(helpMessage(message));
            case FEEDBACK -> feedbackHandler.show(message.getTelegramUserId(), message.getChatId());
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
                    addProductMessage(callback)
            );
            case IMPORT_BASKET -> telegramGateway.sendMessage(
                    importBasketMessage(callback)
            );
            case TRACKED_ITEMS -> trackedItemsHandler.showTracked(
                    callback.getTelegramUserId(),
                    callback.getChatId()
            );
            case REGION -> regionHandler.show(callback.getTelegramUserId(), callback.getChatId());
            case WALLET_DISCOUNT -> { return false; }
            case HELP -> telegramGateway.sendMessage(
                    helpMessage(callback)
            );
            case FEEDBACK -> { return false; }
        }
        return true;
    }

    public boolean showHelpForUnsupportedText(IncomingTelegramMessage message) {
        if (!message.isPrivateChat()) {
            return false;
        }
        telegramGateway.sendMessage(helpMessage(message));
        return true;
    }

    private OutgoingTelegramMessage addProductMessage(IncomingTelegramMessage message) {
        return addProductMessage(message.getTelegramUserId(), message.getChatId());
    }

    private OutgoingTelegramMessage addProductMessage(IncomingTelegramCallback callback) {
        return addProductMessage(callback.getTelegramUserId(), callback.getChatId());
    }

    private OutgoingTelegramMessage addProductMessage(long telegramUserId, long chatId) {
        UserProfile profile = userProfileService.getOrCreate(telegramUserId, chatId);
        int active = subscriptionService.findActive(profile.getId()).size();
        return messageFactory.addProduct(chatId, active, profile.getActiveSubscriptionLimit());
    }

    private OutgoingTelegramMessage importBasketMessage(IncomingTelegramMessage message) {
        return importBasketMessage(message.getTelegramUserId(), message.getChatId());
    }

    private OutgoingTelegramMessage importBasketMessage(IncomingTelegramCallback callback) {
        return importBasketMessage(callback.getTelegramUserId(), callback.getChatId());
    }

    private OutgoingTelegramMessage importBasketMessage(long telegramUserId, long chatId) {
        UserProfile profile = userProfileService.getOrCreate(telegramUserId, chatId);
        int active = subscriptionService.findActive(profile.getId()).size();
        return messageFactory.importBasket(chatId, active, profile.getActiveSubscriptionLimit());
    }

    private OutgoingTelegramMessage helpMessage(IncomingTelegramMessage message) {
        UserProfile profile = userProfileService.getOrCreate(
                message.getTelegramUserId(), message.getChatId()
        );
        return messageFactory.help(message.getChatId(), profile.getActiveSubscriptionLimit());
    }

    private OutgoingTelegramMessage helpMessage(IncomingTelegramCallback callback) {
        UserProfile profile = userProfileService.getOrCreate(
                callback.getTelegramUserId(), callback.getChatId()
        );
        return messageFactory.help(callback.getChatId(), profile.getActiveSubscriptionLimit());
    }

    private boolean isLegacyMenuCommand(String text) {
        return text.equals("/menu") || text.startsWith("/menu@");
    }

}
