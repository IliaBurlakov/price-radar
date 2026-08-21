package com.priceradar.notification.application;

import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.telegram.application.OutgoingTelegramMessage;
import com.priceradar.telegram.application.TelegramDisplayFormatter;
import com.priceradar.user.domain.UserPricePreferences;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public final class NotificationMessageRenderer {

    private static final String APPROXIMATE_PRICE_WARNING =
            "⚠️ Цена может отличаться в приложении Wildberries.";
    private static final DateTimeFormatter OBSERVED_AT_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm 'МСК'", Locale.ROOT)
            .withZone(ZoneId.of("Europe/Moscow"));

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
        notification.getVariantDisplayName()
                .flatMap(TelegramDisplayFormatter::variant)
                .ifPresent(variant -> text.append("\n").append(variant));

        if (notification.getType() == NotificationType.PRICE_DECREASE) {
            text.append("\n\nПредыдущая цена: ")
                    .append(format(notification.getPreviousPrice().orElseThrow()));
        } else {
            text.append("\n\nЦелевая цена: ")
                    .append(format(notification.getTargetPrice().orElseThrow()));
        }
        text.append("\nНовая цена: ").append(format(notification.getCurrentPrice()));
        appendWalletEstimate(text, notification);
        text.append("\nРегион: ")
                .append(TelegramDisplayFormatter.region(notification.getCityName()));
        text.append("\nПроверено: ").append(OBSERVED_AT_FORMAT.format(notification.getObservedAt()));
        text.append("\nОткрыть товар: ").append(notification.getCanonicalUrl());
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
        text.append("\nС WB Кошельком: ≈ ")
                .append(format(estimate.getAmount()))
                .append(" (скидка ")
                .append(estimate.getWalletDiscountPercent())
                .append("%)");
    }

    private String format(RubleAmount amount) {
        return RublePriceFormatter.format(amount);
    }
}
