package com.priceradar.notification.application;

import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.telegram.application.OutgoingTelegramMessage;
import com.priceradar.user.domain.UserPricePreferences;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public final class NotificationMessageRenderer {

    private static final String APPROXIMATE_PRICE_WARNING =
            "⚠️ Цена приблизительная и может отличаться в вашем аккаунте Wildberries.";
    private static final DateTimeFormatter OBSERVED_AT_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final WalletEstimateService walletEstimateService;

    public NotificationMessageRenderer(WalletEstimateService walletEstimateService) {
        if (walletEstimateService == null) {
            throw new IllegalArgumentException("walletEstimateService must not be null");
        }
        this.walletEstimateService = walletEstimateService;
    }

    public OutgoingTelegramMessage render(PendingNotificationDelivery notification) {
        if (notification == null) {
            throw new IllegalArgumentException("notification must not be null");
        }

        StringBuilder text = new StringBuilder();
        appendHeading(text, notification.getType());
        text.append('\n').append(notification.getProductTitle().orElse(
                "Товар Wildberries #" + notification.getExternalProductId()
        ));
        notification.getBrand().ifPresent(brand -> text.append("\nБренд: ").append(brand));
        notification.getVariantDisplayName().ifPresent(variant ->
                text.append("\nВариант: ").append(variant));

        if (notification.getType() == NotificationType.PRICE_DECREASE) {
            text.append("\n\nПредыдущая цена: ")
                    .append(format(notification.getPreviousPrice().orElseThrow()));
        } else {
            text.append("\n\nЦелевая цена: ")
                    .append(format(notification.getTargetPrice().orElseThrow()));
        }
        text.append("\nНовая обычная цена: ").append(format(notification.getCurrentPrice()));
        appendWalletEstimate(text, notification);
        text.append("\nРегион: ").append(notification.getCityName());
        text.append("\nНаблюдение: ").append(OBSERVED_AT_FORMAT.format(notification.getObservedAt()));
        text.append("\nСсылка: ").append(notification.getCanonicalUrl());
        text.append("\n\n").append(APPROXIMATE_PRICE_WARNING);

        return OutgoingTelegramMessage.text(notification.getChatId(), text.toString());
    }

    private void appendHeading(StringBuilder text, NotificationType type) {
        if (type == NotificationType.PRICE_DECREASE) {
            text.append("🔔 Цена снизилась");
            return;
        }
        text.append("🎯 Целевая цена достигнута");
    }

    private void appendWalletEstimate(
            StringBuilder text,
            PendingNotificationDelivery notification
    ) {
        InterpretedPrice regularPrice = new InterpretedPrice(
                Optional.of(notification.getCurrentPrice()),
                Optional.empty(),
                Optional.of(PriceSource.PRODUCT),
                SnapshotStatus.REGULAR_PRICE
        );
        WalletEstimate estimate = walletEstimateService.estimate(
                regularPrice,
                new UserPricePreferences(notification.getWalletDiscountPercent())
        ).orElseThrow();
        text.append("\nС WB Кошельком (оценка, скидка ")
                .append(estimate.getWalletDiscountPercent())
                .append("%): ")
                .append(format(estimate.getAmount()))
                .append(" · ESTIMATED_BY_PERCENT");
    }

    private String format(RubleAmount amount) {
        long minorUnits = amount.getMinorUnits();
        long rubles = minorUnits / 100;
        long kopecks = minorUnits % 100;
        String wholePart = String.format(Locale.forLanguageTag("ru-RU"), "%,d", rubles);
        if (kopecks == 0) {
            return wholePart + " ₽";
        }
        return wholePart + "," + String.format(Locale.ROOT, "%02d", kopecks) + " ₽";
    }
}
