package com.priceradar.telegram.application;

import com.priceradar.product.application.InvalidProductUrlException;
import com.priceradar.product.application.ResolvedQuoteResult;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelegramCurrentQuoteHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramCurrentQuoteHandler.class);
    private static final String START_MESSAGE = """
            PriceRadar показывает приблизительную цену товара Wildberries и помогает начать отслеживание.
            Отправьте ссылку вида https://www.wildberries.ru/catalog/123456/detail.aspx
            Список активных подписок: /tracked
            """;
    private static final String HELP_MESSAGE = """
            Отправьте ссылку на товар Wildberries. Я покажу последнюю полученную цену, регион и оценку цены с WB Кошельком.
            Команда /tracked показывает активные подписки и позволяет остановить отслеживание.
            Цены приблизительные и могут отличаться в вашем аккаунте.
            """;

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
        if (isCommand(text, "/start")) {
            telegramGateway.sendMessage(OutgoingTelegramMessage.text(message.getChatId(), START_MESSAGE));
            return;
        }
        if (isCommand(text, "/help")) {
            telegramGateway.sendMessage(OutgoingTelegramMessage.text(message.getChatId(), HELP_MESSAGE));
            return;
        }
        if (text.isBlank()) {
            telegramGateway.sendMessage(OutgoingTelegramMessage.text(message.getChatId(), HELP_MESSAGE));
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
            return OutgoingTelegramMessage.text(
                    message.getChatId(),
                    "Некорректная ссылка. Отправьте URL вида "
                            + "https://www.wildberries.ru/catalog/123456/detail.aspx"
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to process Telegram current quote, errorType={}",
                    exception.getClass().getSimpleName()
            );
            return OutgoingTelegramMessage.text(
                    message.getChatId(),
                    "Не удалось обработать ссылку из-за внутренней ошибки. Попробуйте позже."
            );
        }
    }

    private boolean isCommand(String text, String command) {
        return text.equals(command) || text.startsWith(command + "@");
    }
}
