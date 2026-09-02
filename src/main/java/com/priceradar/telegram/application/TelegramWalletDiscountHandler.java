package com.priceradar.telegram.application;

import com.priceradar.user.application.PendingWalletDiscountInput;
import com.priceradar.user.application.PendingWalletDiscountInputStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class TelegramWalletDiscountHandler {

    private final UserProfileService userProfileService;
    private final PendingWalletDiscountInputStore pendingInputStore;
    private final TelegramGateway telegramGateway;
    private final Clock clock;
    private final Duration pendingInputTtl;

    public TelegramWalletDiscountHandler(
            UserProfileService userProfileService,
            PendingWalletDiscountInputStore pendingInputStore,
            TelegramGateway telegramGateway,
            Clock clock,
            Duration pendingInputTtl
    ) {
        if (userProfileService == null || pendingInputStore == null
                || telegramGateway == null || clock == null || pendingInputTtl == null
                || pendingInputTtl.isZero() || pendingInputTtl.isNegative()) {
            throw new IllegalArgumentException("wallet discount handler configuration is invalid");
        }
        this.userProfileService = userProfileService;
        this.pendingInputStore = pendingInputStore;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
        this.pendingInputTtl = pendingInputTtl;
    }

    public boolean supportsCallback(IncomingTelegramCallback callback) {
        return callback != null && MainMenuCallbackData.parse(callback.getData())
                .filter(action -> action == MainMenuCallbackData.Action.WALLET_DISCOUNT)
                .isPresent();
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        if (!supportsCallback(callback)) {
            return false;
        }
        if (!callback.isPrivateChat()) {
            return true;
        }
        show(callback.getTelegramUserId(), callback.getChatId());
        return true;
    }

    public void show(long telegramUserId, long chatId) {
        UserProfile profile = userProfileService.getOrCreate(telegramUserId, chatId);
        Instant now = clock.instant();
        pendingInputStore.replace(new PendingWalletDiscountInput(
                profile.getId(), profile.getTelegramUserId(), profile.getTelegramChatId(),
                now, now.plus(pendingInputTtl)
        ));
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                chatId,
                ("💳 WB Кошелёк\n\nПо умолчанию скидка с WB кошельком составляет 3%%. Если у вас " +
                        "увеличенный размер скидки, вы можете задать его здесь.\n\n" +
                        "Текущая скидка: %d%%.\n\n")
                        .formatted(profile.getPricePreferences().getWalletDiscountPercent())
                        + "Введите размер скидки вашего WB Кошелька:",
                TelegramNavigationKeyboard.home()
        ));
    }

    public void cancel(long telegramUserId, long chatId) {
        pendingInputStore.remove(telegramUserId, chatId);
    }

    public boolean handlePendingMessage(IncomingTelegramMessage message) {
        if (message == null || !message.isPrivateChat()) {
            return false;
        }
        if (TelegramBotCommand.isCommandText(message.getText())) {
            pendingInputStore.remove(message.getTelegramUserId(), message.getChatId());
            return false;
        }
        Optional<PendingWalletDiscountInput> found = pendingInputStore.find(
                message.getTelegramUserId(), message.getChatId()
        );
        if (found.isEmpty()) {
            return false;
        }
        PendingWalletDiscountInput pending = found.orElseThrow();
        Instant now = clock.instant();
        if (pending.isExpired(now)) {
            pendingInputStore.remove(message.getTelegramUserId(), message.getChatId());
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    message.getChatId(),
                    "Время ввода скидки истекло. Откройте настройку WB Кошелька ещё раз.",
                    TelegramNavigationKeyboard.mainMenu()
            ));
            return true;
        }
        if (isUrl(message.getText())) {
            pendingInputStore.remove(message.getTelegramUserId(), message.getChatId());
            return false;
        }
        Optional<Integer> parsed = parsePercent(message.getText());
        if (parsed.isEmpty()) {
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    message.getChatId(),
                    "Введите целое число от 2 до 20, например 5.",
                    TelegramNavigationKeyboard.home()
            ));
            return true;
        }
        UserProfile profile = userProfileService.getOrCreate(
                message.getTelegramUserId(), message.getChatId()
        );
        if (!pending.getUserId().equals(profile.getId())) {
            pendingInputStore.remove(message.getTelegramUserId(), message.getChatId());
            return false;
        }
        int walletDiscountPercent = parsed.orElseThrow();
        userProfileService.updateWalletDiscountPercent(
                message.getTelegramUserId(), message.getChatId(), walletDiscountPercent
        );
        pendingInputStore.remove(message.getTelegramUserId(), message.getChatId());
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                message.getChatId(),
                "✅ Скидка WB Кошелька изменена: %d%%.".formatted(walletDiscountPercent),
                TelegramNavigationKeyboard.mainMenu()
        ));
        return true;
    }

    private Optional<Integer> parsePercent(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[0-9]{1,3}")) {
            return Optional.empty();
        }
        int parsed = Integer.parseInt(normalized);
        if (parsed < UserPricePreferences.MIN_WALLET_DISCOUNT_PERCENT
                || parsed > UserPricePreferences.MAX_WALLET_DISCOUNT_PERCENT) {
            return Optional.empty();
        }
        return Optional.of(parsed);
    }

    private boolean isUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }
}
