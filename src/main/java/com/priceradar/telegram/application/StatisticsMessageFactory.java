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
import java.util.UUID;

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

        StringBuilder text = new StringBuilder("📊 Статистика ")
                .append(periodLabel(statistics.getPeriod()))
                .append("\n🗓 ")
                .append(TelegramDisplayFormatter.date(statistics.getEffectivePeriodStart()))
                .append(" — ")
                .append(TelegramDisplayFormatter.date(statistics.getEffectivePeriodEnd()));

        if (!statistics.hasData()) {
            text.append("\n\nЗа этот период пока недостаточно данных.");
            text.append("\n\n🌍 Город: ")
                    .append(TelegramDisplayFormatter.region(region));
            text.append("\n\nИстория ведётся с момента добавления товара.");
            return withItemNavigation(chatId, text.toString(), statistics.getSubscriptionId());
        }

        RubleAmount firstPrice = statistics.getFirstPrice().orElseThrow();
        RubleAmount latestPrice = statistics.getLatestPrice().orElseThrow();
        long priceChangeMinorUnits = statistics.getPriceChangeMinorUnits().orElseThrow();
        text.append("\n\n💰 Последняя известная цена\n");
        appendPriceLineWithWallet(
                text,
                latestPrice,
                preferences
        );
        text.append("\n\n")
                .append(trendIcon(priceChangeMinorUnits))
                .append(" Изменение за период")
                .append("\n")
                .append(format(firstPrice))
                .append(" → ")
                .append(format(latestPrice))
                .append("\n")
                .append(formatSignedChange(priceChangeMinorUnits))
                .append(" (")
                .append(formatSignedPercent(statistics.getPriceChangePercent().orElseThrow()))
                .append(")");

        text.append("\n\n🏷 Минимум\n");
        appendPriceLineWithWallet(
                text,
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
            text.append("\nСейчас цена на ")
                    .append(format(differenceFromMinimum))
                    .append(" выше минимума.");
        }

        text.append("\n\n📌 За период")
                .append("\nМаксимум: ")
                .append(format(statistics.getMaximumPrice().orElseThrow()));
        BigDecimal averageMinorUnits = statistics.getAverageMinorUnits().orElseThrow();
        text.append("\nСредняя: ")
                .append(formatAverage(averageMinorUnits));
        text.append("\nПроверок цены: ")
                .append(statistics.getObservationCount());
        text.append("\n\n🌍 Город: ")
                .append(TelegramDisplayFormatter.region(region));
        text.append("\n\nℹ️ WB Кошелёк: оценка со скидкой ")
                .append(preferences.getWalletDiscountPercent())
                .append("%.");
        text.append("\nИстория ведётся с момента добавления товара.");
        return withItemNavigation(chatId, text.toString(), statistics.getSubscriptionId());
    }

    private OutgoingTelegramMessage withItemNavigation(
            long chatId,
            String text,
            UUID subscriptionId
    ) {
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.itemSubscreen(subscriptionId)
        );
    }

    private String periodLabel(StatisticsPeriod period) {
        return switch (period) {
            case LAST_7_DAYS -> "за 7 дней";
            case LAST_30_DAYS -> "за 30 дней";
            case LAST_365_DAYS -> "за 365 дней";
            case ALL_TIME -> "за всё время отслеживания";
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

    private String trendIcon(long priceChangeMinorUnits) {
        if (priceChangeMinorUnits < 0) {
            return "📉";
        }
        if (priceChangeMinorUnits > 0) {
            return "📈";
        }
        return "➖";
    }

    private void appendPriceLineWithWallet(
            StringBuilder text,
            RubleAmount regularPrice,
            UserPricePreferences preferences
    ) {
        WalletEstimate estimate = walletEstimateService
                .estimateFromRegularPrice(regularPrice, preferences)
                .orElseThrow();
        text.append(format(regularPrice))
                .append(" · с WB Кошельком ")
                .append(TelegramDisplayFormatter.walletAmount(estimate));
    }
}
