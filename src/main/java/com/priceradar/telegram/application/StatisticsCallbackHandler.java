package com.priceradar.telegram.application;

import com.priceradar.statistics.application.SubscriptionStatistics;
import com.priceradar.statistics.application.SubscriptionStatisticsService;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;

public class StatisticsCallbackHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsCallbackHandler.class);

    private final UserProfileService userProfileService;
    private final SubscriptionStatisticsService statisticsService;
    private final StatisticsMessageFactory messageFactory;
    private final TelegramGateway telegramGateway;
    private final Clock clock;

    public StatisticsCallbackHandler(
            UserProfileService userProfileService,
            SubscriptionStatisticsService statisticsService,
            StatisticsMessageFactory messageFactory,
            TelegramGateway telegramGateway,
            Clock clock
    ) {
        if (userProfileService == null || statisticsService == null || messageFactory == null
                || telegramGateway == null || clock == null) {
            throw new IllegalArgumentException("statistics callback handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.statisticsService = statisticsService;
        this.messageFactory = messageFactory;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        Optional<StatisticsCallbackData> callbackData = StatisticsCallbackData.parse(
                callback.getData()
        );
        if (callbackData.isEmpty()) {
            return false;
        }

        try {
            if (!callback.isPrivateChat()) {
                return true;
            }
            UserProfile profile = userProfileService.getOrCreate(
                    callback.getTelegramUserId(),
                    callback.getChatId()
            );
            StatisticsCallbackData data = callbackData.get();
            Optional<SubscriptionStatistics> statistics = statisticsService.calculate(
                    profile.getId(),
                    data.getSubscriptionId(),
                    data.getPeriod(),
                    clock.instant()
            );
            OutgoingTelegramMessage message = statistics
                    .map(value -> messageFactory.create(
                            callback.getChatId(),
                            value,
                            profile.getPriceContext().getCityName()
                    ))
                    .orElseGet(() -> notFoundMessage(callback.getChatId()));
            telegramGateway.sendMessage(message);
            return true;
        } finally {
            answerCallbackBestEffort(callback.getCallbackQueryId());
        }
    }

    private OutgoingTelegramMessage notFoundMessage(long chatId) {
        return OutgoingTelegramMessage.text(
                chatId,
                "Активная подписка не найдена или уже удалена."
                        + "\n\nОбновить список: /tracked"
        );
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
