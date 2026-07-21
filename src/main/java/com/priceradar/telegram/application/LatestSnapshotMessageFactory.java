package com.priceradar.telegram.application;

import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.LatestSnapshotView;
import com.priceradar.user.application.UserProfile;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class LatestSnapshotMessageFactory {

    private static final String APPROXIMATE_PRICE_WARNING =
            "⚠️ Цены приблизительные и могут отличаться в вашем аккаунте Wildberries.";
    private static final DateTimeFormatter OBSERVED_AT_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

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
        snapshot.getVariantDisplayName().ifPresent(variant ->
                text.append("\nВариант: ").append(variant));

        if (!snapshot.hasSnapshot()) {
            text.append("\n\nСохранённых наблюдений пока нет.");
            text.append("\nРегион: ").append(profile.getPriceContext().getCityName());
            text.append("\nСсылка: ").append(snapshot.getCanonicalUrl());
            text.append("\n\nНовый запрос к Wildberries не выполнялся.");
            return OutgoingTelegramMessage.text(chatId, text.toString());
        }

        InterpretedPrice price = snapshot.getInterpretedPrice().orElseThrow();
        text.append("\n\nПоследнее сохранённое наблюдение: ")
                .append(OBSERVED_AT_FORMAT.format(snapshot.getObservedAt().orElseThrow()));
        appendPrice(text, price, profile);
        text.append("\nРегион: ").append(profile.getPriceContext().getCityName());
        text.append("\nСсылка: ").append(snapshot.getCanonicalUrl());
        text.append("\n\nИсточник: сохранённый snapshot. Новый запрос к Wildberries не выполнялся.");
        text.append("\n").append(APPROXIMATE_PRICE_WARNING);
        return OutgoingTelegramMessage.text(chatId, text.toString());
    }

    private void appendPrice(
            StringBuilder text,
            InterpretedPrice price,
            UserProfile profile
    ) {
        SnapshotStatus status = price.getStatus();
        if (status == SnapshotStatus.REGULAR_PRICE) {
            text.append("\nОбычная цена: ")
                    .append(format(price.getRegularPrice().orElseThrow()));
            price.getMarketingBasePrice().ifPresent(marketing -> text
                    .append("\nМаркетинговая цена: ")
                    .append(format(marketing))
                    .append(" (не историческая цена)"));
            walletEstimateService.estimate(price, profile.getPricePreferences())
                    .ifPresent(estimate -> appendWalletEstimate(text, estimate));
            text.append("\nНаличие: в наличии");
            return;
        }
        if (status == SnapshotStatus.BASIC_FALLBACK) {
            text.append("\nМаркетинговая цена (fallback): ")
                    .append(format(price.getMarketingBasePrice().orElseThrow()));
            text.append("\nНадёжная обычная цена недоступна.");
            text.append("\nНаличие: в наличии");
            return;
        }
        if (status == SnapshotStatus.UNAVAILABLE) {
            text.append("\nСостояние: товар недоступен");
            text.append("\nНадёжная обычная цена недоступна.");
            return;
        }
        text.append("\nСостояние: цена не найдена");
        text.append("\nНаличие: в наличии, но надёжной цены нет.");
    }

    private void appendWalletEstimate(
            StringBuilder text,
            WalletEstimate estimate
    ) {
        text.append("\nС WB Кошельком (оценка, скидка ")
                .append(estimate.getWalletDiscountPercent())
                .append("%): ")
                .append(format(estimate.getAmount()))
                .append(" · ESTIMATED_BY_PERCENT");
    }

    private String format(RubleAmount amount) {
        long rubles = amount.getMinorUnits() / 100;
        long kopecks = amount.getMinorUnits() % 100;
        return kopecks == 0
                ? rubles + " ₽"
                : rubles + "," + String.format(Locale.ROOT, "%02d", kopecks) + " ₽";
    }
}
