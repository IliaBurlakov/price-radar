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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramOnboardingHandlerTest {

    private static final long TELEGRAM_ID = 7001L;

    @Test
    void unconfiguredUserCanUseStartHelpAndMainMenuButBusinessCommandsRequireCity() {
        UserProfileService users = mock(UserProfileService.class);
        TelegramRegionHandler regions = mock(TelegramRegionHandler.class);
        UserProfile profile = profile(false);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        TelegramOnboardingHandler handler = new TelegramOnboardingHandler(users, regions);

        for (String text : new String[]{"/start", "/help", "/menu"}) {
            clearInvocations(regions);

            assertThat(handler.handleMessage(message(text))).isFalse();

            verify(regions, never()).showOnboarding(profile);
        }

        for (String text : new String[]{"/add", "/import",
                "https://www.wildberries.ru/catalog/123456/detail.aspx"}) {
            clearInvocations(regions);

            assertThat(handler.handleMessage(message(text))).isTrue();

            verify(regions).showOnboarding(profile);
        }

        IncomingTelegramMessage city = message("Томск");
        assertThat(handler.handleMessage(city)).isTrue();
        verify(regions).handleMessage(city);
    }

    @Test
    void unconfiguredUserCanOpenPublicNavigationCallbacks() {
        UserProfileService users = mock(UserProfileService.class);
        TelegramRegionHandler regions = mock(TelegramRegionHandler.class);
        UserProfile profile = profile(false);
        IncomingTelegramCallback callback = callback(RegionCallbackData.OPEN);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        TelegramOnboardingHandler handler = new TelegramOnboardingHandler(users, regions);

        when(regions.supportsCallback(callback)).thenReturn(true);

        assertThat(handler.handleCallback(callback)).isFalse();

        verify(regions, never()).showOnboarding(profile);

        for (MainMenuCallbackData.Action action : new MainMenuCallbackData.Action[]{
                MainMenuCallbackData.Action.HOME,
                MainMenuCallbackData.Action.HELP,
                MainMenuCallbackData.Action.FEEDBACK,
                MainMenuCallbackData.Action.REGION
        }) {
            assertThat(handler.handleCallback(callback(
                    MainMenuCallbackData.encode(action)
            ))).isFalse();
        }
    }

    @Test
    void unrelatedOldCallbackRedirectsToPickerAndConfiguredUserPassesThrough() {
        UserProfileService users = mock(UserProfileService.class);
        TelegramRegionHandler regions = mock(TelegramRegionHandler.class);
        UserProfile unconfigured = profile(false);
        UserProfile configured = profile(true);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID))
                .thenReturn(unconfigured, unconfigured, unconfigured)
                .thenReturn(configured);
        TelegramOnboardingHandler handler = new TelegramOnboardingHandler(users, regions);

        for (MainMenuCallbackData.Action action : new MainMenuCallbackData.Action[]{
                MainMenuCallbackData.Action.TRACKED_ITEMS,
                MainMenuCallbackData.Action.ADD_PRODUCT,
                MainMenuCallbackData.Action.IMPORT_BASKET
        }) {
            assertThat(handler.handleCallback(callback(
                    MainMenuCallbackData.encode(action)
            ))).isTrue();
        }
        verify(regions, times(3)).showOnboarding(unconfigured);

        IncomingTelegramCallback callback = callback(
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.TRACKED_ITEMS)
        );
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
