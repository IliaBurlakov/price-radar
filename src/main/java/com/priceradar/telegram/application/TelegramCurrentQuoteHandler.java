package com.priceradar.telegram.application;

import com.priceradar.product.application.InvalidProductUrlException;
import com.priceradar.product.application.ResolvedQuoteResult;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;

public class TelegramCurrentQuoteHandler {

    private static final String INPUT_HELP_MESSAGE =
            "Отправьте ссылку на товар Wildberries или откройте /help.";

    private final UserProfileService userProfileService;
    private final ResolvedQuoteService resolvedQuoteService;
    private final TelegramQuoteMessageFactory messageFactory;
    private final TelegramGateway telegramGateway;

    public TelegramCurrentQuoteHandler(
            UserProfileService userProfileService,
            ResolvedQuoteService resolvedQuoteService,
            TelegramQuoteMessageFactory messageFactory,
            TelegramGateway telegramGateway
    ) {
        if (userProfileService == null || resolvedQuoteService == null
                || messageFactory == null || telegramGateway == null) {
            throw new IllegalArgumentException("Telegram quote handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.resolvedQuoteService = resolvedQuoteService;
        this.messageFactory = messageFactory;
        this.telegramGateway = telegramGateway;
    }

    public void handle(IncomingTelegramMessage message) {
        if (!message.isPrivateChat()) {
            return;
        }

        String text = message.getText();
        if (text.isBlank() || text.startsWith("/")) {
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    message.getChatId(),
                    INPUT_HELP_MESSAGE,
                    TelegramNavigationKeyboard.mainMenu()
            ));
            return;
        }

        OutgoingTelegramMessage response = resolveQuote(message);
        telegramGateway.sendMessage(response);
    }

    private OutgoingTelegramMessage resolveQuote(IncomingTelegramMessage message) {
        try {
            UserProfile profile = userProfileService.getOrCreate(
                    message.getTelegramUserId(),
                    message.getChatId()
            );
            ResolvedQuoteResult result = resolvedQuoteService.resolve(
                    message.getText(),
                    profile.getPriceContext()
            );
            if (!result.isSuccess()) {
                return messageFactory.createFailureMessage(message.getChatId(), result);
            }
            return messageFactory.createQuoteMessage(
                    message.getChatId(),
                    result.getQuote().orElseThrow(),
                    profile
            );
        } catch (InvalidProductUrlException exception) {
            return new OutgoingTelegramMessage(
                    message.getChatId(),
                    "Не получилось распознать ссылку. Отправьте ссылку на товар в формате:\n"
                            + "https://www.wildberries.ru/catalog/10302970/detail.aspx",
                    TelegramNavigationKeyboard.addProductAndHome()
            );
        }
    }
}
