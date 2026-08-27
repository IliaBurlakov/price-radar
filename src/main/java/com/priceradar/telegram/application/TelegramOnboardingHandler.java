package com.priceradar.telegram.application;

import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;

public final class TelegramOnboardingHandler {

    private final UserProfileService userProfileService;
    private final TelegramRegionHandler regionHandler;

    public TelegramOnboardingHandler(
            UserProfileService userProfileService,
            TelegramRegionHandler regionHandler
    ) {
        if (userProfileService == null || regionHandler == null) {
            throw new IllegalArgumentException("onboarding handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.regionHandler = regionHandler;
    }

    public boolean handleMessage(IncomingTelegramMessage message) {
        if (message == null || !message.isPrivateChat()) {
            return false;
        }
        UserProfile profile = userProfileService.getOrCreate(
                message.getTelegramUserId(),
                message.getChatId()
        );
        if (profile.isRegionSelected()) {
            return false;
        }
        if (TelegramBotCommand.isCommandText(message.getText()) || containsWildberriesLink(message.getText())) {
            regionHandler.showOnboarding(profile);
        } else {
            regionHandler.handleMessage(message);
        }
        return true;
    }

    private boolean containsWildberriesLink(String text) {
        return text != null && text.toLowerCase(java.util.Locale.ROOT).contains("wildberries.ru");
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        if (callback == null || !callback.isPrivateChat()) {
            return false;
        }
        UserProfile profile = userProfileService.getOrCreate(
                callback.getTelegramUserId(),
                callback.getChatId()
        );
        if (profile.isRegionSelected()) {
            return false;
        }
        regionHandler.showOnboarding(profile);
        return true;
    }
}
