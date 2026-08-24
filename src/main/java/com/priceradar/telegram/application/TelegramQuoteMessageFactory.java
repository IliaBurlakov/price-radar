package com.priceradar.telegram.application;

import com.priceradar.marketplace.application.MarketplaceProviderFailureCode;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.application.ResolvedQuote;
import com.priceradar.product.application.ResolvedQuoteResult;
import com.priceradar.user.application.UserProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TelegramQuoteMessageFactory {

    private final WalletEstimateService walletEstimateService;
    private final TrackingCallbackCodec trackingCallbackCodec;

    public TelegramQuoteMessageFactory(
            WalletEstimateService walletEstimateService,
            TrackingCallbackCodec trackingCallbackCodec
    ) {
        if (walletEstimateService == null || trackingCallbackCodec == null) {
            throw new IllegalArgumentException("quote message dependencies must not be null");
        }
        this.walletEstimateService = walletEstimateService;
        this.trackingCallbackCodec = trackingCallbackCodec;
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
        quote.getBrand().ifPresent(brand -> text.append("\nБренд: ").append(brand));
        appendAvailability(text, quote.getInterpretedPrice());
        appendPrice(text, quote.getInterpretedPrice(), userProfile);
        text.append("\nРегион: ")
                .append(TelegramDisplayFormatter.region(
                        quote.getPriceContext().getCityName()
                ));
        appendAutoSelectedVariant(text, quote);
        text.append("\nОткрыть товар: ").append(quote.getCanonicalUrl());
        text.append("\n\n").append(TelegramDisplayFormatter.approximatePriceWarning());

        return new OutgoingTelegramMessage(
                chatId,
                text.toString(),
                quoteKeyboard(
                        quote.getQuoteSnapshotId(),
                        userProfile.getTelegramUserId()
                )
        );
    }

    public OutgoingTelegramMessage createFailureMessage(long chatId, ResolvedQuoteResult result) {
        if (result == null || result.isSuccess()) {
            throw new IllegalArgumentException("failed result is required");
        }

        String text = result.getProviderFailure()
                .map(failure -> providerFailureText(failure.getCode()))
                .orElseGet(() -> quoteFailureText(result.getFailureCode().orElseThrow()));
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.addProductAndHome()
        );
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
            text.append("\nЦена без WB Кошелька: ").append(format(regularPrice));
            walletEstimateService.estimate(price, userProfile.getPricePreferences())
                    .ifPresent(estimate -> appendWalletEstimate(text, estimate));
            return;
        }

        if (price.getStatus() == SnapshotStatus.BASIC_FALLBACK) {
            text.append("\nДоступная цена: ")
                    .append(format(price.getMarketingBasePrice().orElseThrow()));
            text.append("\nТочную текущую цену сейчас показать не удалось.");
            return;
        }

        if (price.getStatus() == SnapshotStatus.UNAVAILABLE) {
            text.append("\nТекущую цену показать нельзя: товара нет в наличии.");
            return;
        }

        text.append("\nТекущая цена пока недоступна.");
    }

    private void appendWalletEstimate(StringBuilder text, WalletEstimate estimate) {
        text.append("\nС WB Кошельком: ")
                .append(TelegramDisplayFormatter.walletEstimate(estimate));
    }

    private void appendAutoSelectedVariant(StringBuilder text, ResolvedQuote quote) {
        if (!quote.getResolvedVariant().isAutoSelected()) {
            return;
        }
        quote.getResolvedVariant().getDisplayName()
                .flatMap(TelegramDisplayFormatter::variant)
                .ifPresent(displayName -> text.append("\n")
                        .append(displayName)
                        .append(" (выбран автоматически)"));
    }

    private List<List<TelegramInlineButton>> trackingKeyboard(
            UUID quoteSnapshotId,
            long telegramUserId
    ) {
        return List.of(
                List.of(new TelegramInlineButton(
                        "Следить за минимумом",
                        trackingCallbackCodec.encode(
                                TrackingCallbackData.Action.TRACK_ANY_DECREASE,
                                quoteSnapshotId,
                                telegramUserId
                        )
                )),
                List.of(new TelegramInlineButton(
                        "Указать желаемую цену",
                        trackingCallbackCodec.encode(
                                TrackingCallbackData.Action.TRACK_TARGET,
                                quoteSnapshotId,
                                telegramUserId
                        )
                ))
        );
    }

    private List<List<TelegramInlineButton>> quoteKeyboard(
            UUID quoteSnapshotId,
            long telegramUserId
    ) {
        List<List<TelegramInlineButton>> keyboard = new ArrayList<>(trackingKeyboard(
                quoteSnapshotId,
                telegramUserId
        ));
        keyboard.add(TelegramNavigationKeyboard.trackedItemsAndHome().getFirst());
        return List.copyOf(keyboard);
    }

    private String providerFailureText(MarketplaceProviderFailureCode code) {
        return switch (code) {
            case PRODUCT_NOT_FOUND -> "Товар Wildberries не найден.";
            case COOLDOWN_ACTIVE, RATE_LIMITED, ACCESS_FORBIDDEN ->
                    "Сервис временно не может проверить цену. Попробуйте немного позже.";
            case INVALID_REQUEST -> "Не получилось обработать товар. Проверьте ссылку.";
            default -> "Сейчас не получилось проверить цену. Попробуйте позже.";
        };
    }

    private String quoteFailureText(ResolvedQuoteResult.FailureCode code) {
        return switch (code) {
            case VARIANT_NOT_RESOLVED ->
                    "Не получилось выбрать доступный вариант товара.";
            case PRICE_FIELDS_NOT_FOUND -> "Для выбранного варианта цена сейчас недоступна.";
            case PROVIDER_DATA_MISMATCH, PROVIDER_FAILURE ->
                    "Сейчас не получилось обработать данные товара. Попробуйте позже.";
        };
    }

    private String format(RubleAmount amount) {
        return RublePriceFormatter.format(amount);
    }
}
