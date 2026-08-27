package com.priceradar.telegram.application;

import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramOnboardingHandlerTest {

    private static final long TELEGRAM_ID = 7001L;

    @Test
    void unconfiguredUserIsRedirectedForCommandsAndTextIsTreatedAsCityQuery() {
        UserProfileService users = mock(UserProfileService.class);
        TelegramRegionHandler regions = mock(TelegramRegionHandler.class);
        UserProfile profile = profile(false);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        TelegramOnboardingHandler handler = new TelegramOnboardingHandler(users, regions);

        for (String text : new String[]{
                "/start", "/add", "/import",
                "https://www.wildberries.ru/catalog/123456/detail.aspx"
        }) {
            clearInvocations(regions);

            assertThat(handler.handleMessage(message(text))).isTrue();

            verify(regions).showOnboarding(profile);
        }

        IncomingTelegramMessage city = message("Томск");
        assertThat(handler.handleMessage(city)).isTrue();
        verify(regions).handleMessage(city);
    }

    @Test
    void callbackFromUnconfiguredUserReturnsToCityPrompt() {
        UserProfileService users = mock(UserProfileService.class);
        TelegramRegionHandler regions = mock(TelegramRegionHandler.class);
        UserProfile profile = profile(false);
        IncomingTelegramCallback callback = callback(RegionCallbackData.OPEN);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        TelegramOnboardingHandler handler = new TelegramOnboardingHandler(users, regions);

        assertThat(handler.handleCallback(callback)).isTrue();

        verify(regions).showOnboarding(profile);
        verify(regions, never()).handleCallback(callback);
    }

    @Test
    void unrelatedOldCallbackRedirectsToPickerAndConfiguredUserPassesThrough() {
        UserProfileService users = mock(UserProfileService.class);
        TelegramRegionHandler regions = mock(TelegramRegionHandler.class);
        IncomingTelegramCallback callback = callback(
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.TRACKED_ITEMS)
        );
        UserProfile unconfigured = profile(false);
        UserProfile configured = profile(true);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID))
                .thenReturn(unconfigured)
                .thenReturn(configured);
        TelegramOnboardingHandler handler = new TelegramOnboardingHandler(users, regions);

        assertThat(handler.handleCallback(callback)).isTrue();
        verify(regions).showOnboarding(unconfigured);

        assertThat(handler.handleCallback(callback)).isFalse();
    }

    private UserProfile profile(boolean selected) {
        return new UserProfile(
                UUID.randomUUID(), TELEGRAM_ID, TELEGRAM_ID,
                selected ? moscow() : null, UserPricePreferences.defaults()
        );
    }

    private IncomingTelegramMessage message(String text) {
        return new IncomingTelegramMessage(TELEGRAM_ID, TELEGRAM_ID, "private", text);
    }

    private IncomingTelegramCallback callback(String data) {
        return new IncomingTelegramCallback(
                "callback", TELEGRAM_ID, TELEGRAM_ID, "private", data
        );
    }
}
