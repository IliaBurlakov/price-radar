package com.priceradar.telegram.application;

import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.LatestSnapshotView;
import com.priceradar.user.application.UserProfile;

import java.util.List;

public final class LatestSnapshotMessageFactory {

    private final WalletEstimateService walletEstimateService;

    public LatestSnapshotMessageFactory(WalletEstimateService walletEstimateService) {
        if (walletEstimateService == null) {
            throw new IllegalArgumentException("walletEstimateService must not be null");
        }
        this.walletEstimateService = walletEstimateService;
    }

    public OutgoingTelegramMessage create(
            long chatId,
            LatestSnapshotView snapshot,
            UserProfile profile
    ) {
        if (snapshot == null || profile == null) {
            throw new IllegalArgumentException("latest snapshot message fields must not be null");
        }

        StringBuilder text = new StringBuilder();
        text.append(snapshot.getTitle().orElse(
                "Товар Wildberries #" + snapshot.getNmId()
        ));
        snapshot.getBrand().ifPresent(brand -> text.append("\nБренд: ").append(brand));
        snapshot.getVariantDisplayName()
                .flatMap(TelegramDisplayFormatter::variant)
                .ifPresent(variant -> text.append("\n").append(variant));

        if (!snapshot.hasSnapshot()) {
            text.append("\n\nЦена пока не проверялась.");
            appendRegionAndLink(text, snapshot);
            return withTrackedButton(chatId, text.toString());
        }

        InterpretedPrice price = snapshot.getInterpretedPrice().orElseThrow();
        text.append("\n\nПроверено: ")
                .append(TelegramDisplayFormatter.observedAt(
                        snapshot.getObservedAt().orElseThrow()
                ));
        appendPrice(text, price, profile);
        appendRegionAndLink(text, snapshot);
        text.append("\n\nПоказана последняя сохранённая цена.");
        text.append("\n").append(TelegramDisplayFormatter.approximatePriceWarning());
        return withTrackedButton(chatId, text.toString());
    }

    private void appendPrice(
            StringBuilder text,
            InterpretedPrice price,
            UserProfile profile
    ) {
        SnapshotStatus status = price.getStatus();
        if (status == SnapshotStatus.REGULAR_PRICE) {
            text.append("\nЦена без WB Кошелька: ")
                    .append(format(price.getRegularPrice().orElseThrow()));
            walletEstimateService.estimate(price, profile.getPricePreferences())
                    .ifPresent(estimate -> appendWalletEstimate(text, estimate));
            text.append("\nНаличие: в наличии");
            return;
        }
        if (status == SnapshotStatus.BASIC_FALLBACK) {
            text.append("\nДоступная цена: ")
                    .append(format(price.getMarketingBasePrice().orElseThrow()));
            text.append("\nТочную текущую цену сейчас показать не удалось.");
            text.append("\nНаличие: в наличии");
            return;
        }
        if (status == SnapshotStatus.UNAVAILABLE) {
            text.append("\nНаличие: нет в наличии");
            return;
        }
        text.append("\nЦена пока недоступна");
        text.append("\nНаличие: в наличии");
    }

    private void appendWalletEstimate(
            StringBuilder text,
            WalletEstimate estimate
    ) {
        text.append("\nС WB Кошельком: ")
                .append(TelegramDisplayFormatter.walletEstimate(estimate));
    }

    private void appendRegionAndLink(StringBuilder text, LatestSnapshotView snapshot) {
        text.append("\n\nРегион: ")
                .append(TelegramDisplayFormatter.region(
                        snapshot.getPriceContext().getCityName()
                ));
        text.append("\nОткрыть товар:\n").append(snapshot.getCanonicalUrl());
    }

    private OutgoingTelegramMessage withTrackedButton(long chatId, String text) {
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.trackedItemsAndHome()
        );
    }

    private String format(RubleAmount amount) {
        return RublePriceFormatter.format(amount);
    }
}
