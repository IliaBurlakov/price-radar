package com.priceradar.region.application;

import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.region.domain.MarketplaceRegionCode;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileStore;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.irkutsk;
import static com.priceradar.testsupport.TestMarketplaceRegions.bratsk;
import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void changesRegionOnlyWhenThereAreNoActiveSubscriptions() {
        UUID userId = UUID.randomUUID();
        MarketplaceRegionStore regions = mock(MarketplaceRegionStore.class);
        UserProfileStore users = mock(UserProfileStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        UserProfile current = profile(userId, moscow());
        MarketplaceRegion requested = irkutsk();
        UserProfile updated = profile(userId, requested);
        when(users.findByIdAndLock(userId)).thenReturn(Optional.of(current));
        when(regions.findByCode(MarketplaceRegionCode.IRKUTSK)).thenReturn(Optional.of(requested));
        when(subscriptions.countActive(userId)).thenReturn(0L);
        when(users.updateRegion(current, requested, NOW)).thenReturn(updated);

        RegionChangeResult result = new UserRegionService(regions, users, subscriptions)
                .changeRegion(userId, MarketplaceRegionCode.IRKUTSK, NOW);

        assertThat(result.getStatus()).isEqualTo(RegionChangeResult.Status.CHANGED);
        assertThat(result.getRegion()).get().extracting(MarketplaceRegion::getCode)
                .isEqualTo(MarketplaceRegionCode.IRKUTSK);
        verify(users).updateRegion(current, requested, NOW);
    }

    @Test
    void blocksDifferentRegionButAllowsCurrentRegionWithActiveSubscriptions() {
        UUID userId = UUID.randomUUID();
        MarketplaceRegionStore regions = mock(MarketplaceRegionStore.class);
        UserProfileStore users = mock(UserProfileStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        UserProfile current = profile(userId, moscow());
        when(users.findByIdAndLock(userId)).thenReturn(Optional.of(current));
        when(regions.findByCode(MarketplaceRegionCode.IRKUTSK)).thenReturn(Optional.of(irkutsk()));
        when(regions.findByCode(MarketplaceRegionCode.MOSCOW)).thenReturn(Optional.of(moscow()));
        when(subscriptions.countActive(userId)).thenReturn(3L);
        UserRegionService service = new UserRegionService(regions, users, subscriptions);

        RegionChangeResult blocked = service.changeRegion(
                userId, MarketplaceRegionCode.IRKUTSK, NOW
        );
        RegionChangeResult noOp = service.changeRegion(
                userId, MarketplaceRegionCode.MOSCOW, NOW
        );

        assertThat(blocked.getStatus()).isEqualTo(RegionChangeResult.Status.ACTIVE_SUBSCRIPTIONS);
        assertThat(blocked.getActiveSubscriptions()).isEqualTo(3);
        assertThat(noOp.getStatus()).isEqualTo(RegionChangeResult.Status.UNCHANGED);
        verify(users, never()).updateRegion(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void initialMoscowSelectionIsNotTreatedAsUnchanged() {
        UUID userId = UUID.randomUUID();
        MarketplaceRegionStore regions = mock(MarketplaceRegionStore.class);
        UserProfileStore users = mock(UserProfileStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        UserProfile unconfigured = profile(userId, moscow(), false);
        UserProfile configured = profile(userId, moscow(), true);
        when(users.findByIdAndLock(userId)).thenReturn(Optional.of(unconfigured));
        when(regions.findByCode(MarketplaceRegionCode.MOSCOW))
                .thenReturn(Optional.of(moscow()));
        when(users.updateRegion(
                org.mockito.ArgumentMatchers.eq(unconfigured),
                org.mockito.ArgumentMatchers.any(MarketplaceRegion.class),
                org.mockito.ArgumentMatchers.eq(NOW)
        )).thenReturn(configured);

        RegionChangeResult result = new UserRegionService(regions, users, subscriptions)
                .changeRegion(userId, MarketplaceRegionCode.MOSCOW, NOW);

        assertThat(result.getStatus()).isEqualTo(RegionChangeResult.Status.SELECTED);
        assertThat(result.getRegion()).get().extracting(MarketplaceRegion::getCode)
                .isEqualTo(MarketplaceRegionCode.MOSCOW);
        verify(users).updateRegion(
                org.mockito.ArgumentMatchers.eq(unconfigured),
                org.mockito.ArgumentMatchers.any(MarketplaceRegion.class),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
        verify(subscriptions, never()).countActive(userId);
    }

    @Test
    void initialDifferentRegionSelectionUsesSelectedStatus() {
        UUID userId = UUID.randomUUID();
        MarketplaceRegionStore regions = mock(MarketplaceRegionStore.class);
        UserProfileStore users = mock(UserProfileStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        UserProfile unconfigured = profile(userId, moscow(), false);
        UserProfile configured = profile(userId, bratsk(), true);
        when(users.findByIdAndLock(userId)).thenReturn(Optional.of(unconfigured));
        when(regions.findByCode(MarketplaceRegionCode.BRATSK))
                .thenReturn(Optional.of(bratsk()));
        when(subscriptions.countActive(userId)).thenReturn(0L);
        when(users.updateRegion(
                org.mockito.ArgumentMatchers.eq(unconfigured),
                org.mockito.ArgumentMatchers.any(MarketplaceRegion.class),
                org.mockito.ArgumentMatchers.eq(NOW)
        )).thenReturn(configured);

        RegionChangeResult result = new UserRegionService(regions, users, subscriptions)
                .changeRegion(userId, MarketplaceRegionCode.BRATSK, NOW);

        assertThat(result.getStatus()).isEqualTo(RegionChangeResult.Status.SELECTED);
        assertThat(result.getRegion()).get().extracting(MarketplaceRegion::getCode)
                .isEqualTo(MarketplaceRegionCode.BRATSK);
    }

    private UserProfile profile(UUID userId, MarketplaceRegion region) {
        return profile(userId, region, true);
    }

    private UserProfile profile(UUID userId, MarketplaceRegion region, boolean selected) {
        return new UserProfile(
                userId, 7001L, 7001L, region, UserPricePreferences.defaults(), selected
        );
    }
}
