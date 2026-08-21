package com.priceradar.telegram.application;

import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.statistics.application.SubscriptionStatistics;
import com.priceradar.statistics.domain.StatisticsPeriod;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class StatisticsMessageFactory {

    private static final String APPROXIMATE_PRICE_WARNING =
            "⚠️ Цена может отличаться в приложении Wildberries.";
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy", Locale.ROOT)
            .withZone(ZoneId.of("Europe/Moscow"));

    public OutgoingTelegramMessage create(
            long chatId,
            SubscriptionStatistics statistics,
            String region
    ) {
        if (statistics == null || region == null || region.isBlank()) {
            throw new IllegalArgumentException("statistics message fields must not be null or blank");
        }

        StringBuilder text = new StringBuilder("Статистика ")
                .append(periodLabel(statistics.getPeriod()))
                .append("\nПериод: ")
                .append(PERIOD_FORMAT.format(statistics.getEffectivePeriodStart()))
                .append(" — ")
                .append(PERIOD_FORMAT.format(statistics.getEffectivePeriodEnd()));

        if (!statistics.hasData()) {
            text.append("\n\nЗа этот период пока недостаточно данных.");
            text.append("\nРегион: ")
                    .append(TelegramDisplayFormatter.region(region));
            text.append("\nИстория начинается с момента добавления товара.");
            text.append("\n").append(APPROXIMATE_PRICE_WARNING);
            return withTrackedButton(chatId, text.toString());
        }

        text.append("\n\nМинимальная цена: ")
                .append(format(statistics.getMinimumPrice().orElseThrow()));
        text.append("\nМаксимальная цена: ")
                .append(format(statistics.getMaximumPrice().orElseThrow()));
        text.append("\nСредняя цена: ")
                .append(formatAverage(statistics.getAverageMinorUnits().orElseThrow()));
        text.append("\nПроверок с доступной ценой: ")
                .append(statistics.getObservationCount());
        text.append("\nРегион: ")
                .append(TelegramDisplayFormatter.region(region));
        text.append("\nИстория начинается с момента добавления товара.");
        text.append("\n").append(APPROXIMATE_PRICE_WARNING);
        return withTrackedButton(chatId, text.toString());
    }

    private OutgoingTelegramMessage withTrackedButton(long chatId, String text) {
        return new OutgoingTelegramMessage(
                chatId,
                text,
                List.of(List.of(new TelegramInlineButton(
                        "Мои товары",
                        MainMenuCallbackData.encode(MainMenuCallbackData.Action.TRACKED_ITEMS)
                )))
        );
    }

    private String periodLabel(StatisticsPeriod period) {
        return switch (period) {
            case LAST_7_DAYS -> "за последние 7 дней";
            case LAST_30_DAYS -> "за последние 30 дней";
            case LAST_365_DAYS -> "за последние 365 дней";
            case ALL_TIME -> "за всё время текущей подписки";
        };
    }

    private String formatAverage(BigDecimal averageMinorUnits) {
        return RublePriceFormatter.formatMinorUnits(averageMinorUnits);
    }

    private String format(RubleAmount amount) {
        return RublePriceFormatter.format(amount);
    }
}
