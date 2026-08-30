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
        regionHandler.showOnboarding(profile);
        return true;
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
        if (regionHandler.supportsCallback(callback)) {
            return regionHandler.handleCallback(callback);
        }
        regionHandler.showOnboarding(profile);
        return true;
    }
}
