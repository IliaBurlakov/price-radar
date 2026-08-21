package com.priceradar.telegram.application;

import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.LatestSnapshotView;
import com.priceradar.user.application.UserProfile;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class LatestSnapshotMessageFactory {

    private static final String APPROXIMATE_PRICE_WARNING =
            "⚠️ Цена может отличаться в приложении Wildberries.";
    private static final DateTimeFormatter OBSERVED_AT_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm 'МСК'", Locale.ROOT)
            .withZone(ZoneId.of("Europe/Moscow"));

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
                .append(OBSERVED_AT_FORMAT.format(snapshot.getObservedAt().orElseThrow()));
        appendPrice(text, price, profile);
        appendRegionAndLink(text, snapshot);
        text.append("\n\nПоказана последняя сохранённая цена.");
        text.append("\n").append(APPROXIMATE_PRICE_WARNING);
        return withTrackedButton(chatId, text.toString());
    }

    private void appendPrice(
            StringBuilder text,
            InterpretedPrice price,
            UserProfile profile
    ) {
        SnapshotStatus status = price.getStatus();
        if (status == SnapshotStatus.REGULAR_PRICE) {
            text.append("\nЦена: ")
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
        text.append("\nС WB Кошельком: ≈ ")
                .append(format(estimate.getAmount()))
                .append(" (скидка ")
                .append(estimate.getWalletDiscountPercent())
                .append("%)");
    }

    private void appendRegionAndLink(StringBuilder text, LatestSnapshotView snapshot) {
        text.append("\nРегион: ")
                .append(TelegramDisplayFormatter.region(
                        snapshot.getPriceContext().getCityName()
                ));
        text.append("\nОткрыть товар: ").append(snapshot.getCanonicalUrl());
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

    private String format(RubleAmount amount) {
        return RublePriceFormatter.format(amount);
    }
}
