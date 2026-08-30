package com.priceradar.telegram.application;

import com.priceradar.region.application.CitySelectionResult;
import com.priceradar.region.application.CitySelectionService;
import com.priceradar.region.application.PendingCitySelection;
import com.priceradar.region.application.GeoLocationLabelFormatter;
import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TelegramRegionHandler {

    private static final String SEARCHING_MESSAGE = "🔎 Ищу город...";
    private static final String APPLYING_MESSAGE = "⏳ Устанавливаю город...";

    private final UserProfileService userProfileService;
    private final CitySelectionService citySelectionService;
    private final TelegramGateway telegramGateway;
    private final Clock clock;
    private final GeoLocationLabelFormatter locationLabelFormatter;

    public TelegramRegionHandler(
            UserProfileService userProfileService,
            CitySelectionService citySelectionService,
            TelegramGateway telegramGateway,
            Clock clock,
            GeoLocationLabelFormatter locationLabelFormatter
    ) {
        if (userProfileService == null || citySelectionService == null
                || telegramGateway == null || clock == null || locationLabelFormatter == null) {
            throw new IllegalArgumentException("region handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.citySelectionService = citySelectionService;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
        this.locationLabelFormatter = locationLabelFormatter;
    }

    public void show(long telegramUserId, long chatId) {
        UserProfile profile = userProfileService.getOrCreate(telegramUserId, chatId);
        showPrompt(profile);
    }

    public void showOnboarding(UserProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile must not be null");
        showPrompt(profile);
    }

    public boolean handleMessage(IncomingTelegramMessage message) {
        if (message == null || !message.isPrivateChat() || TelegramBotCommand.isCommandText(message.getText())) {
            return false;
        }
        UserProfile profile = userProfileService.getOrCreate(
                message.getTelegramUserId(), message.getChatId()
        );
        Instant now = clock.instant();
        if (profile.isRegionSelected() && !citySelectionService.hasPending(profile, now)) {
            return false;
        }
        handleInput(profile, message.getText(), now);
        return true;
    }

    public boolean supportsCallback(IncomingTelegramCallback callback) {
        return callback != null && RegionCallbackData.OPEN.equals(callback.getData());
    }

    public void clearPendingInput(long telegramUserId, long chatId) {
        UserProfile profile = userProfileService.getOrCreate(telegramUserId, chatId);
        citySelectionService.cancel(profile);
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        if (!supportsCallback(callback)) return false;
        if (callback.isPrivateChat()) show(callback.getTelegramUserId(), callback.getChatId());
        return true;
    }

    private void showPrompt(UserProfile profile) {
        Instant now = clock.instant();
        citySelectionService.begin(profile, now);
        String current = profile.getLocation()
                .map(location -> "\n\nСейчас выбран: "
                        + locationLabelFormatter.format(location.toCandidate()) + '.')
                .orElse("");
        String text = "📍 Выберите город" + current + "\n\n"
                + "Введите название города, для которого нужно отслеживать цены Wildberries.\n\n"
                + "Например:\nМосква\n\n";
        List<List<TelegramInlineButton>> keyboard = profile.isRegionSelected()
                ? TelegramNavigationKeyboard.home()
                : List.of();
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                profile.getTelegramChatId(), text, keyboard
        ));
    }

    private void handleInput(UserProfile profile, String input, Instant now) {
        Optional<PendingCitySelection> pending = citySelectionService.pending(profile, now);
        Optional<Integer> number = parseNumber(input);
        if (number.isPresent() && pending.filter(value -> !value.getCandidates().isEmpty()).isPresent()) {
            int selectedNumber = number.orElseThrow();
            int optionCount = pending.orElseThrow().getCandidates().size();
            if (selectedNumber < 1 || selectedNumber > optionCount) {
                telegramGateway.sendMessage(invalidNumber(profile.getTelegramChatId(), optionCount));
                return;
            }
            telegramGateway.sendMessage(OutgoingTelegramMessage.text(
                    profile.getTelegramChatId(), APPLYING_MESSAGE
            ));
            CitySelectionResult result = citySelectionService.choose(profile, selectedNumber, now);
            telegramGateway.sendMessage(resultMessage(profile.getTelegramChatId(), profile, result, now));
            return;
        }
        telegramGateway.sendMessage(OutgoingTelegramMessage.text(
                profile.getTelegramChatId(), SEARCHING_MESSAGE
        ));
        CitySelectionResult result = citySelectionService.search(profile, input, now);
        telegramGateway.sendMessage(resultMessage(profile.getTelegramChatId(), profile, result, now));
    }

    private OutgoingTelegramMessage resultMessage(
            long chatId,
            UserProfile currentProfile,
            CitySelectionResult result,
            Instant now
    ) {
        return switch (result.getStatus()) {
            case SELECTED -> success(chatId, "Город выбран", result);
            case CHANGED -> success(chatId, "Город изменён", result);
            case UNCHANGED -> success(chatId, "Город уже выбран", result);
            case OPTIONS -> options(chatId, result.getCandidates());
            case INVALID_NUMBER -> invalidNumber(chatId, currentProfile, now);
            case NOT_FOUND -> retry(chatId,
                    "Не удалось найти такой город. Проверьте название и попробуйте ещё раз.");
            case GEOCODING_UNAVAILABLE -> retry(chatId,
                    "⚠️ Сейчас не удалось найти город. Попробуйте ещё раз немного позже.");
            case WILDBERRIES_UNAVAILABLE -> retry(chatId,
                    "⚠️ Сейчас не удалось подготовить данные для выбранного города. Попробуйте ещё раз немного позже.");
            case ACTIVE_SUBSCRIPTIONS -> blockedChange(chatId, result.getActiveSubscriptions());
            case SESSION_EXPIRED -> retry(chatId,
                    "Выбор города устарел. Отправьте название города ещё раз.");
            case USER_NOT_FOUND -> new OutgoingTelegramMessage(
                    chatId, "Не удалось загрузить ваши данные. Отправьте /start и попробуйте ещё раз.",
                    TelegramNavigationKeyboard.home()
            );
        };
    }

    private OutgoingTelegramMessage success(long chatId, String action, CitySelectionResult result) {
        String city = locationLabelFormatter.format(
                result.getLocation().orElseThrow().getLocation().toCandidate()
        );
        return new OutgoingTelegramMessage(
                chatId,
                "✅ " + action + ": " + city + ".\n\nТеперь можно добавлять товары и импортировать корзину.",
                TelegramNavigationKeyboard.mainMenu()
        );
    }

    private OutgoingTelegramMessage options(long chatId, List<GeoCandidate> candidates) {
        StringBuilder text = new StringBuilder("🔎 Нашёл несколько вариантов:\n\n");
        for (int index = 0; index < candidates.size(); index++) {
            text.append(index + 1).append(". ")
                    .append(locationLabelFormatter.format(candidates.get(index))).append('\n');
        }
        text.append("\nОтправьте номер нужного варианта.");
        return new OutgoingTelegramMessage(chatId, text.toString(), TelegramNavigationKeyboard.home());
    }

    private OutgoingTelegramMessage invalidNumber(long chatId, UserProfile profile, Instant now) {
        int size = citySelectionService.pending(profile, now)
                .map(selection -> selection.getCandidates().size())
                .orElse(0);
        return invalidNumber(chatId, size);
    }

    private OutgoingTelegramMessage invalidNumber(long chatId, int size) {
        return new OutgoingTelegramMessage(
                chatId,
                "Введите номер от 1 до " + size + ".",
                TelegramNavigationKeyboard.home()
        );
    }

    private OutgoingTelegramMessage retry(long chatId, String message) {
        return new OutgoingTelegramMessage(
                chatId,
                message + "\n\nВведите название города.",
                TelegramNavigationKeyboard.home()
        );
    }

    private OutgoingTelegramMessage blockedChange(long chatId, long activeSubscriptions) {
        String text = "⚠️ Пока нельзя изменить город.\n\n"
                + "У вас есть активные отслеживания: " + activeSubscriptions + ". "
                + "Чтобы история цен оставалась корректной, город нельзя менять во время отслеживания товаров.\n\n"
                + "Чтобы выбрать другой город, сначала остановите все активные отслеживания.";
        return new OutgoingTelegramMessage(chatId, text, List.of(
                List.of(new TelegramInlineButton("🧹 Очистить все", ClearTrackingCallbackData.START)),
                List.of(TelegramNavigationKeyboard.button(
                        "Мои товары", MainMenuCallbackData.Action.TRACKED_ITEMS
                )),
                List.of(TelegramNavigationKeyboard.button("Главное меню", MainMenuCallbackData.Action.HOME))
        ));
    }

    private Optional<Integer> parseNumber(String value) {
        try {
            String normalized = value == null ? "" : value.trim();
            if (!normalized.matches("[0-9]{1,3}")) return Optional.empty();
            return Optional.of(Integer.parseInt(normalized));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
