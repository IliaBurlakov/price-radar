package com.priceradar.telegram.application;

import com.priceradar.marketplace.application.MarketplaceProviderFailureCode;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.application.ResolvedQuote;
import com.priceradar.product.application.ResolvedQuoteResult;
import com.priceradar.user.application.UserProfile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TelegramQuoteMessageFactory {

    private static final String APPROXIMATE_PRICE_WARNING =
            "⚠️ Цена приблизительная и может отличаться в вашем аккаунте Wildberries.";

    private final WalletEstimateService walletEstimateService;

    public TelegramQuoteMessageFactory(WalletEstimateService walletEstimateService) {
        if (walletEstimateService == null) {
            throw new IllegalArgumentException("walletEstimateService must not be null");
        }
        this.walletEstimateService = walletEstimateService;
    }

    public OutgoingTelegramMessage createQuoteMessage(
            long chatId,
            ResolvedQuote quote,
            UserProfile userProfile
    ) {
        if (quote == null || userProfile == null) {
            throw new IllegalArgumentException("quote and userProfile must not be null");
        }

        StringBuilder text = new StringBuilder();
        text.append(quote.getTitle().orElse("Товар Wildberries #" + quote.getNmId()));
        text.append("\nБренд: ").append(quote.getBrand().orElse("не указан"));
        appendAvailability(text, quote.getInterpretedPrice());
        appendPrice(text, quote.getInterpretedPrice(), userProfile);
        text.append("\nРегион: ").append(quote.getPriceContext().getCityName());
        appendAutoSelectedVariant(text, quote);
        text.append("\nСсылка: ").append(quote.getCanonicalUrl());
        text.append("\n\n").append(APPROXIMATE_PRICE_WARNING);

        return new OutgoingTelegramMessage(
                chatId,
                text.toString(),
                trackingKeyboard(quote.getWatchTargetId())
        );
    }

    public OutgoingTelegramMessage createFailureMessage(long chatId, ResolvedQuoteResult result) {
        if (result == null || result.isSuccess()) {
            throw new IllegalArgumentException("failed result is required");
        }

        String text = result.getProviderFailure()
                .map(failure -> providerFailureText(failure.getCode()))
                .orElseGet(() -> quoteFailureText(result.getFailureCode().orElseThrow()));
        return OutgoingTelegramMessage.text(chatId, text);
    }

    private void appendAvailability(StringBuilder text, InterpretedPrice price) {
        String availability = price.getStatus() == SnapshotStatus.UNAVAILABLE
                ? "нет в наличии"
                : "в наличии";
        text.append("\nНаличие: ").append(availability);
    }

    private void appendPrice(
            StringBuilder text,
            InterpretedPrice price,
            UserProfile userProfile
    ) {
        if (price.getStatus() == SnapshotStatus.REGULAR_PRICE) {
            RubleAmount regularPrice = price.getRegularPrice().orElseThrow();
            text.append("\nОбычная цена: ").append(format(regularPrice));
            walletEstimateService.estimate(price, userProfile.getPricePreferences())
                    .ifPresent(estimate -> appendWalletEstimate(text, estimate));
            return;
        }

        if (price.getStatus() == SnapshotStatus.BASIC_FALLBACK) {
            text.append("\nМаркетинговая цена (fallback): ")
                    .append(format(price.getMarketingBasePrice().orElseThrow()));
            text.append("\nНадёжная обычная цена сейчас недоступна.");
            return;
        }

        if (price.getStatus() == SnapshotStatus.UNAVAILABLE) {
            text.append("\nНадёжную текущую цену показать нельзя: товар недоступен.");
            return;
        }

        text.append("\nНадёжная текущая цена пока недоступна.");
    }

    private void appendWalletEstimate(StringBuilder text, WalletEstimate estimate) {
        text.append("\nС WB Кошельком (оценка, скидка ")
                .append(estimate.getWalletDiscountPercent())
                .append("%): ")
                .append(format(estimate.getAmount()));
    }

    private void appendAutoSelectedVariant(StringBuilder text, ResolvedQuote quote) {
        if (!quote.getResolvedVariant().isAutoSelected()) {
            return;
        }
        quote.getResolvedVariant().getDisplayName()
                .ifPresent(displayName -> text.append("\nАвтоматически выбран вариант: ")
                        .append(displayName));
    }

    private List<List<TelegramInlineButton>> trackingKeyboard(UUID watchTargetId) {
        return List.of(
                List.of(new TelegramInlineButton(
                        "Отслеживать любое снижение",
                        callbackData("TRACK_ANY_DECREASE", watchTargetId)
                )),
                List.of(new TelegramInlineButton(
                        "Установить целевую цену",
                        callbackData("TRACK_TARGET", watchTargetId)
                ))
        );
    }

    private String callbackData(String action, UUID applicationId) {
        return action + ":" + applicationId;
    }

    private String providerFailureText(MarketplaceProviderFailureCode code) {
        return switch (code) {
            case PRODUCT_NOT_FOUND -> "Товар Wildberries не найден.";
            case COOLDOWN_ACTIVE, RATE_LIMITED, ACCESS_FORBIDDEN ->
                    "Wildberries временно ограничил запросы. Попробуйте отправить ссылку позже.";
            case INVALID_REQUEST -> "Не удалось обработать данные товара. Проверьте ссылку.";
            default -> "Сейчас не удалось получить данные Wildberries. Попробуйте позже.";
        };
    }

    private String quoteFailureText(ResolvedQuoteResult.FailureCode code) {
        return switch (code) {
            case VARIANT_NOT_RESOLVED ->
                    "Не удалось определить доступный вариант товара с обычной ценой.";
            case PRICE_FIELDS_NOT_FOUND -> "Для выбранного варианта цена сейчас недоступна.";
            case PROVIDER_DATA_MISMATCH, PROVIDER_FAILURE ->
                    "Сейчас не удалось безопасно обработать данные товара. Попробуйте позже.";
        };
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
