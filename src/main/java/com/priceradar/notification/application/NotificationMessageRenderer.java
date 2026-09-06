package com.priceradar.notification.application;

import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.telegram.application.OutgoingTelegramMessage;
import com.priceradar.telegram.application.TelegramDisplayFormatter;
import com.priceradar.telegram.application.TelegramNavigationKeyboard;
import com.priceradar.user.domain.UserPricePreferences;

import java.util.Optional;

public final class NotificationMessageRenderer {

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

        UserPricePreferences preferences = new UserPricePreferences(
                notification.getWalletDiscountPercent()
        );
        if (notification.getType() == NotificationType.PRICE_DECREASE) {
            RubleAmount previousPrice = notification.getPreviousPrice().orElseThrow();
            text.append("\n\nПредыдущая цена без WB Кошелька: ")
                    .append(format(previousPrice));
            appendWalletEstimate(
                    text,
                    previousPrice,
                    preferences,
                    "Предыдущая цена",
                    false
            );
        } else {
            text.append("\n\nЦелевая цена: ")
                    .append(format(notification.getTargetPrice().orElseThrow()));
        }
        text.append("\n\nНовая цена без WB Кошелька: ")
                .append(format(notification.getCurrentPrice()));
        appendWalletEstimate(
                text,
                notification.getCurrentPrice(),
                preferences,
                "Новая цена",
                true
        );
        text.append("\n\nЦена проверена: ")
                .append(TelegramDisplayFormatter.observedAt(notification.getObservedAt()));
        text.append("\n\nОткрыть товар:\n").append(notification.getCanonicalUrl());

        return new OutgoingTelegramMessage(
                notification.getChatId(),
                text.toString(),
                TelegramNavigationKeyboard.trackedItemsAndHome()
        );
    }

    private void appendHeading(StringBuilder text, NotificationType type) {
        if (type == NotificationType.PRICE_DECREASE) {
            text.append("🔔 Новая минимальная цена");
            return;
        }
        text.append("🎯 Целевая цена достигнута");
    }

    private void appendWalletEstimate(
            StringBuilder text,
            RubleAmount regularPrice,
            UserPricePreferences preferences,
            String label,
            boolean includeDiscount
    ) {
        WalletEstimate estimate = walletEstimateService.estimateFromRegularPrice(
                regularPrice,
                preferences
        ).orElseThrow();
        text.append("\n")
                .append(label)
                .append(" с WB Кошельком: ")
                .append(includeDiscount
                        ? TelegramDisplayFormatter.walletEstimate(estimate)
                        : TelegramDisplayFormatter.walletAmount(estimate));
    }

    private String format(RubleAmount amount) {
        return RublePriceFormatter.format(amount);
    }
}
