package com.priceradar.telegram.application;

import com.priceradar.statistics.application.SubscriptionStatistics;
import com.priceradar.statistics.application.SubscriptionStatisticsService;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        Optional<UUID> menuSubscriptionId = StatisticsMenuCallbackData.parse(
                callback.getData()
        );
        Optional<StatisticsCallbackData> callbackData = StatisticsCallbackData.parse(
                callback.getData()
        );
        if (menuSubscriptionId.isEmpty() && callbackData.isEmpty()) {
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
            Instant now = clock.instant();
            if (menuSubscriptionId.isPresent()) {
                showPeriodMenu(callback, profile, menuSubscriptionId.orElseThrow(), now);
                return true;
            }
            StatisticsCallbackData data = callbackData.get();
            Optional<SubscriptionStatistics> statistics = statisticsService.calculate(
                    profile.getId(),
                    data.getSubscriptionId(),
                    data.getPeriod(),
                    now
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

    private void showPeriodMenu(
            IncomingTelegramCallback callback,
            UserProfile profile,
            UUID subscriptionId,
            Instant now
    ) {
        boolean activeAndOwned = statisticsService.calculate(
                profile.getId(),
                subscriptionId,
                com.priceradar.statistics.domain.StatisticsPeriod.ALL_TIME,
                now
        ).isPresent();
        if (!activeAndOwned) {
            telegramGateway.sendMessage(notFoundMessage(callback.getChatId()));
            return;
        }
        List<List<TelegramInlineButton>> keyboard = List.of(
                List.of(
                        periodButton("7 дней", subscriptionId,
                                com.priceradar.statistics.domain.StatisticsPeriod.LAST_7_DAYS),
                        periodButton("30 дней", subscriptionId,
                                com.priceradar.statistics.domain.StatisticsPeriod.LAST_30_DAYS)
                ),
                List.of(
                        periodButton("365 дней", subscriptionId,
                                com.priceradar.statistics.domain.StatisticsPeriod.LAST_365_DAYS),
                        periodButton("Всё время", subscriptionId,
                                com.priceradar.statistics.domain.StatisticsPeriod.ALL_TIME)
                ),
                List.of(new TelegramInlineButton(
                        "Мои товары",
                        MainMenuCallbackData.encode(MainMenuCallbackData.Action.TRACKED_ITEMS)
                ))
        );
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                callback.getChatId(),
                "За какой период показать статистику?",
                keyboard
        ));
    }

    private TelegramInlineButton periodButton(
            String text,
            UUID subscriptionId,
            com.priceradar.statistics.domain.StatisticsPeriod period
    ) {
        return new TelegramInlineButton(
                text,
                StatisticsCallbackData.encode(subscriptionId, period)
        );
    }

    private OutgoingTelegramMessage notFoundMessage(long chatId) {
        return OutgoingTelegramMessage.text(
                chatId,
                "Этот товар больше не отслеживается. Откройте раздел «Мои товары»."
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
