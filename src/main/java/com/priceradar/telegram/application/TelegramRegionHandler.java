package com.priceradar.telegram.application;

import com.priceradar.region.application.RegionChangeResult;
import com.priceradar.region.application.UserRegionService;
import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.region.domain.MarketplaceRegionCode;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TelegramRegionHandler {

    private final UserProfileService userProfileService;
    private final UserRegionService userRegionService;
    private final TelegramGateway telegramGateway;
    private final Clock clock;

    public TelegramRegionHandler(
            UserProfileService userProfileService,
            UserRegionService userRegionService,
            TelegramGateway telegramGateway,
            Clock clock
    ) {
        if (userProfileService == null || userRegionService == null
                || telegramGateway == null || clock == null) {
            throw new IllegalArgumentException("region handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.userRegionService = userRegionService;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
    }

    public void show(long telegramUserId, long chatId) {
        UserProfile profile = userProfileService.getOrCreate(telegramUserId, chatId);
        telegramGateway.sendMessage(regionSelection(chatId, profile));
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        boolean open = RegionCallbackData.OPEN.equals(callback.getData());
        Optional<MarketplaceRegionCode> selected = RegionCallbackData.parseSelection(callback.getData());
        if (!open && selected.isEmpty()) {
            return false;
        }
        if (!callback.isPrivateChat()) {
            return true;
        }
        UserProfile profile = userProfileService.getOrCreate(
                callback.getTelegramUserId(), callback.getChatId()
        );
        if (open) {
            telegramGateway.sendMessage(regionSelection(callback.getChatId(), profile));
            return true;
        }
        RegionChangeResult result = userRegionService.changeRegion(
                profile.getId(), selected.orElseThrow(), clock.instant()
        );
        telegramGateway.sendMessage(changeResult(callback.getChatId(), profile, result));
        return true;
    }

    private OutgoingTelegramMessage regionSelection(long chatId, UserProfile profile) {
        List<List<TelegramInlineButton>> keyboard = new ArrayList<>();
        List<TelegramInlineButton> row = new ArrayList<>(2);
        for (MarketplaceRegion region : userRegionService.listEnabled()) {
            String marker = region.getCode() == profile.getRegion().getCode() ? "✓ " : "";
            row.add(new TelegramInlineButton(
                    marker + region.getDisplayName(), RegionCallbackData.select(region.getCode())
            ));
            if (row.size() == 2) {
                keyboard.add(List.copyOf(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            keyboard.add(List.copyOf(row));
        }
        keyboard.add(List.of(TelegramNavigationKeyboard.button(
                "← Назад", MainMenuCallbackData.Action.HOME
        )));
        String text = "🌍 Город\n\nСейчас выбран: "
                + profile.getRegion().getDisplayName()
                + ".\n\nЦены и наличие товаров могут отличаться в разных городах. "
                + "Выберите город:";
        return new OutgoingTelegramMessage(chatId, text, keyboard);
    }

    private OutgoingTelegramMessage changeResult(
            long chatId,
            UserProfile currentProfile,
            RegionChangeResult result
    ) {
        return switch (result.getStatus()) {
            case CHANGED -> new OutgoingTelegramMessage(
                    chatId,
                    "✅ Город изменён: "
                            + result.getRegion().orElseThrow().getDisplayName()
                            + ".\n\nНовые товары и корзины будут проверяться для выбранного города.",
                    TelegramNavigationKeyboard.mainMenu()
            );
            case UNCHANGED -> regionSelection(chatId, currentProfile);
            case ACTIVE_SUBSCRIPTIONS -> blockedChange(chatId, result);
            case REGION_NOT_FOUND -> new OutgoingTelegramMessage(
                    chatId,
                    "Этот город сейчас недоступен. Выберите другой.",
                    List.of(List.of(new TelegramInlineButton("← К городам", RegionCallbackData.OPEN)))
            );
            case USER_NOT_FOUND -> new OutgoingTelegramMessage(
                    chatId, "Не удалось найти профиль. Откройте главное меню и попробуйте ещё раз.",
                    TelegramNavigationKeyboard.home()
            );
        };
    }

    private OutgoingTelegramMessage blockedChange(long chatId, RegionChangeResult result) {
        String requested = result.getRegion().orElseThrow().getDisplayName();
        String text = "⚠️ Пока нельзя изменить город на " + requested + ".\n\n"
                + "У вас есть активные отслеживания: " + result.getActiveSubscriptions() + ". "
                + "Город влияет на цену и наличие, поэтому существующую историю "
                + "нельзя безопасно перенести в другой город.\n\n"
                + "Чтобы выбрать другой город, сначала остановите все активные отслеживания.";
        return new OutgoingTelegramMessage(chatId, text, List.of(
                List.of(new TelegramInlineButton("🧹 Очистить все", ClearTrackingCallbackData.START)),
                List.of(TelegramNavigationKeyboard.button(
                        "Мои товары", MainMenuCallbackData.Action.TRACKED_ITEMS
                )),
                List.of(new TelegramInlineButton("← К городам", RegionCallbackData.OPEN))
        ));
    }
}
