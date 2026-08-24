package com.priceradar.telegram.application;

import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.statistics.application.SubscriptionStatistics;
import com.priceradar.statistics.domain.StatisticsPeriod;
import com.priceradar.user.domain.UserPricePreferences;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class StatisticsMessageFactory {

    private final WalletEstimateService walletEstimateService;

    public StatisticsMessageFactory(WalletEstimateService walletEstimateService) {
        if (walletEstimateService == null) {
            throw new IllegalArgumentException("walletEstimateService must not be null");
        }
        this.walletEstimateService = walletEstimateService;
    }

    public OutgoingTelegramMessage create(
            long chatId,
            SubscriptionStatistics statistics,
            String region,
            UserPricePreferences preferences
    ) {
        if (statistics == null || region == null || region.isBlank() || preferences == null) {
            throw new IllegalArgumentException("statistics message fields must not be null or blank");
        }

        StringBuilder text = new StringBuilder("Статистика ")
                .append(periodLabel(statistics.getPeriod()))
                .append("\nПериод: ")
                .append(TelegramDisplayFormatter.date(statistics.getEffectivePeriodStart()))
                .append(" — ")
                .append(TelegramDisplayFormatter.date(statistics.getEffectivePeriodEnd()));

        if (!statistics.hasData()) {
            text.append("\n\nЗа этот период пока недостаточно данных.");
            text.append("\nРегион: ")
                    .append(TelegramDisplayFormatter.region(region));
            text.append("\nИстория начинается с момента добавления товара.");
            text.append("\n").append(TelegramDisplayFormatter.approximatePriceWarning());
            return withTrackedButton(chatId, text.toString());
        }

        RubleAmount firstPrice = statistics.getFirstPrice().orElseThrow();
        RubleAmount latestPrice = statistics.getLatestPrice().orElseThrow();
        text.append("\n");
        appendPriceWithWallet(
                text,
                "Последняя известная цена",
                latestPrice,
                preferences
        );
        text.append("\n\nИзменение за период:")
                .append("\n")
                .append(format(firstPrice))
                .append(" → ")
                .append(format(latestPrice))
                .append("\n")
                .append(formatSignedChange(statistics.getPriceChangeMinorUnits().orElseThrow()))
                .append(" (")
                .append(formatSignedPercent(statistics.getPriceChangePercent().orElseThrow()))
                .append(")");

        text.append("\n");
        appendPriceWithWallet(
                text,
                "Минимум",
                statistics.getMinimumPrice().orElseThrow(),
                preferences
        );
        text.append("\nЗафиксирован: ")
                .append(TelegramDisplayFormatter.observedAt(
                        statistics.getMinimumObservedAt().orElseThrow()
                ));
        RubleAmount differenceFromMinimum = statistics
                .getLatestPriceDifferenceFromMinimum()
                .orElseThrow();
        if (differenceFromMinimum.getMinorUnits() == 0) {
            text.append("\nПоследняя известная цена — минимальная за выбранный период.");
        } else {
            text.append("\nПоследняя цена на ")
                    .append(format(differenceFromMinimum))
                    .append(" выше минимума.");
        }

        text.append("\n");
        appendPriceWithWallet(
                text,
                "Максимум",
                statistics.getMaximumPrice().orElseThrow(),
                preferences
        );
        BigDecimal averageMinorUnits = statistics.getAverageMinorUnits().orElseThrow();
        appendPriceWithWallet(
                text,
                "Средняя",
                averageAmount(averageMinorUnits),
                preferences,
                formatAverage(averageMinorUnits)
        );
        text.append("\nОценка WB Кошелька рассчитана со скидкой ")
                .append(preferences.getWalletDiscountPercent())
                .append("%.");
        text.append("\nНаблюдений: ")
                .append(statistics.getObservationCount());
        text.append("\nРегион: ")
                .append(TelegramDisplayFormatter.region(region));
        text.append("\nИстория начинается с момента добавления товара.");
        text.append("\n").append(TelegramDisplayFormatter.approximatePriceWarning());
        return withTrackedButton(chatId, text.toString());
    }

    private OutgoingTelegramMessage withTrackedButton(long chatId, String text) {
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.trackedItemsAndHome()
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

    private String formatSignedChange(long minorUnits) {
        String sign = minorUnits > 0 ? "+" : minorUnits < 0 ? "−" : "";
        return sign + format(RubleAmount.ofMinorUnits(Math.abs(minorUnits)));
    }

    private String formatSignedPercent(BigDecimal percent) {
        BigDecimal displayValue = percent.abs()
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        String sign = percent.signum() > 0 ? "+" : percent.signum() < 0 ? "−" : "";
        return sign + displayValue.toPlainString().replace('.', ',') + "%";
    }

    private void appendPriceWithWallet(
            StringBuilder text,
            String label,
            RubleAmount regularPrice,
            UserPricePreferences preferences
    ) {
        appendPriceWithWallet(
                text,
                label,
                regularPrice,
                preferences,
                format(regularPrice)
        );
    }

    private void appendPriceWithWallet(
            StringBuilder text,
            String label,
            RubleAmount regularPrice,
            UserPricePreferences preferences,
            String formattedRegularPrice
    ) {
        WalletEstimate estimate = walletEstimateService
                .estimateFromRegularPrice(regularPrice, preferences)
                .orElseThrow();
        text.append("\n")
                .append(label)
                .append(": ")
                .append(formattedRegularPrice)
                .append(" · с WB Кошельком ")
                .append(TelegramDisplayFormatter.walletAmount(estimate));
    }

    private RubleAmount averageAmount(BigDecimal averageMinorUnits) {
        return RubleAmount.ofMinorUnits(averageMinorUnits
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact());
    }
}
