package com.priceradar.telegram.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.statistics.application.SubscriptionStatistics;
import com.priceradar.statistics.domain.StatisticsPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class StatisticsMessageFactory {

    private static final String APPROXIMATE_PRICE_WARNING =
            "⚠️ Цены приблизительные и могут отличаться в вашем аккаунте Wildberries.";
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    public OutgoingTelegramMessage create(
            long chatId,
            SubscriptionStatistics statistics,
            String region
    ) {
        if (statistics == null || region == null || region.isBlank()) {
            throw new IllegalArgumentException("statistics message fields must not be null or blank");
        }

        StringBuilder text = new StringBuilder("Статистика обычной цены ")
                .append(periodLabel(statistics.getPeriod()))
                .append("\nПериод: ")
                .append(PERIOD_FORMAT.format(statistics.getEffectivePeriodStart()))
                .append(" — ")
                .append(PERIOD_FORMAT.format(statistics.getEffectivePeriodEnd()));

        if (!statistics.hasData()) {
            text.append("\n\nСтатистика пока недоступна: в выбранном периоде ещё нет "
                    + "наблюдений обычной цены.");
            text.append("\nРегион: ").append(region.trim());
            text.append("\n\nУчитываются только обычные цены, наблюдавшиеся в текущем "
                    + "периоде подписки.");
            text.append("\n").append(APPROXIMATE_PRICE_WARNING);
            return OutgoingTelegramMessage.text(chatId, text.toString());
        }

        text.append("\n\nМинимальная цена: ")
                .append(format(statistics.getMinimumPrice().orElseThrow()));
        text.append("\nМаксимальная цена: ")
                .append(format(statistics.getMaximumPrice().orElseThrow()));
        text.append("\nСредняя цена: ")
                .append(formatAverage(statistics.getAverageMinorUnits().orElseThrow()));
        text.append("\nКоличество наблюдений: ")
                .append(statistics.getObservationCount());
        text.append("\nРегион: ").append(region.trim());
        text.append("\n\nУчитываются только обычные цены, наблюдавшиеся в текущем "
                + "периоде подписки.");
        text.append("\n").append(APPROXIMATE_PRICE_WARNING);
        return OutgoingTelegramMessage.text(chatId, text.toString());
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
        long roundedMinorUnits = averageMinorUnits
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return format(RubleAmount.ofMinorUnits(roundedMinorUnits));
    }

    private String format(RubleAmount amount) {
        long rubles = amount.getMinorUnits() / 100;
        long kopecks = amount.getMinorUnits() % 100;
        return kopecks == 0
                ? rubles + " ₽"
                : rubles + "," + String.format(Locale.ROOT, "%02d", kopecks) + " ₽";
    }
}
