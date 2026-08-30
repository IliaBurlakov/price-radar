package com.priceradar.region.application;

import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileStore;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.irkutsk;
import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    @Test
    void initialSelectionConfiguresPreviouslyUnconfiguredUser() {
        UUID userId = UUID.randomUUID();
        UserProfileStore users = mock(UserProfileStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        ResolvedLocation requested = moscow();
        UserProfile unconfigured = profile(userId, null, false);
        UserProfile configured = profile(userId, requested, true);
        when(users.findByIdAndLock(userId)).thenReturn(Optional.of(unconfigured));
        when(users.updateLocation(unconfigured, requested, NOW)).thenReturn(configured);

        RegionChangeResult result = new UserRegionService(users, subscriptions)
                .changeLocation(userId, requested, NOW);

        assertThat(result.getStatus()).isEqualTo(RegionChangeResult.Status.SELECTED);
        assertThat(result.getLocation()).contains(requested);
        verify(subscriptions, never()).countActive(userId);
    }

    @Test
    void activeSubscriptionBlocksLocationChange() {
        UUID userId = UUID.randomUUID();
        UserProfileStore users = mock(UserProfileStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        ResolvedLocation currentLocation = moscow();
        ResolvedLocation requested = irkutsk();
        UserProfile current = profile(userId, currentLocation, true);
        when(users.findByIdAndLock(userId)).thenReturn(Optional.of(current));
        when(subscriptions.countActive(userId)).thenReturn(2L);

        RegionChangeResult result = new UserRegionService(users, subscriptions)
                .changeLocation(userId, requested, NOW);

        assertThat(result.getStatus()).isEqualTo(RegionChangeResult.Status.ACTIVE_SUBSCRIPTIONS);
        assertThat(result.getActiveSubscriptions()).isEqualTo(2);
        verify(users, never()).updateLocation(current, requested, NOW);
    }

    @Test
    void locationCanChangeAfterAllSubscriptionsAreStopped() {
        UUID userId = UUID.randomUUID();
        UserProfileStore users = mock(UserProfileStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        ResolvedLocation requested = irkutsk();
        UserProfile current = profile(userId, moscow(), true);
        UserProfile changed = profile(userId, requested, true);
        when(users.findByIdAndLock(userId)).thenReturn(Optional.of(current));
        when(subscriptions.countActive(userId)).thenReturn(0L);
        when(users.updateLocation(current, requested, NOW)).thenReturn(changed);

        RegionChangeResult result = new UserRegionService(users, subscriptions)
                .changeLocation(userId, requested, NOW);

        assertThat(result.getStatus()).isEqualTo(RegionChangeResult.Status.CHANGED);
        assertThat(result.getLocation()).contains(requested);
    }

    @Test
    void selectingCurrentLocationIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UserProfileStore users = mock(UserProfileStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        ResolvedLocation currentLocation = moscow();
        UserProfile current = profile(userId, currentLocation, true);
        when(users.findByIdAndLock(userId)).thenReturn(Optional.of(current));

        RegionChangeResult result = new UserRegionService(users, subscriptions)
                .changeLocation(userId, currentLocation, NOW);

        assertThat(result.getStatus()).isEqualTo(RegionChangeResult.Status.UNCHANGED);
        verify(subscriptions, never()).countActive(userId);
        verify(users, never()).updateLocation(current, currentLocation, NOW);
    }

    private UserProfile profile(UUID userId, ResolvedLocation location, boolean configured) {
        return new UserProfile(
                userId, 1001L, 1001L, configured ? location : null, UserPricePreferences.defaults()
        );
    }
}
