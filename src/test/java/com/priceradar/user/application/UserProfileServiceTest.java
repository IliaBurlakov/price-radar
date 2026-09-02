package com.priceradar.user.application;

import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T08:00:00Z");

    @Test
    void updatesOnlyTheRequestedUsersWalletDiscount() {
        UserProfileStore store = mock(UserProfileStore.class);
        UserProfile existing = new UserProfile(
                UUID.randomUUID(), 7001L, 7001L, null, UserPricePreferences.defaults()
        );
        UserProfile updated = new UserProfile(
                existing.getId(), 7001L, 7001L, null, new UserPricePreferences(5)
        );
        when(store.findByTelegramUserId(7001L)).thenReturn(Optional.of(existing));
        when(store.updatePricePreferences(
                existing, new UserPricePreferences(5), NOW
        )).thenReturn(updated);
        UserProfileService service = new UserProfileService(
                store, UserPricePreferences.defaults(), Clock.fixed(NOW, ZoneOffset.UTC)
        );

        UserProfile result = service.updateWalletDiscountPercent(7001L, 7001L, 5);

        assertThat(result.getPricePreferences().getWalletDiscountPercent()).isEqualTo(5);
        verify(store).updatePricePreferences(existing, new UserPricePreferences(5), NOW);
        verify(store, never()).create(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void createsNewUserWithConfiguredSubscriptionLimit() {
        UserProfileStore store = mock(UserProfileStore.class);
        UserProfile created = new UserProfile(
                UUID.randomUUID(), 7002L, 7002L, null,
                UserPricePreferences.defaults(), 10
        );
        when(store.findByTelegramUserId(7002L)).thenReturn(Optional.empty());
        when(store.create(
                7002L, 7002L, UserPricePreferences.defaults(), 10, NOW
        )).thenReturn(created);
        UserProfileService service = new UserProfileService(
                store, UserPricePreferences.defaults(), 10,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        UserProfile result = service.getOrCreate(7002L, 7002L);

        assertThat(result.getActiveSubscriptionLimit()).isEqualTo(10);
        verify(store).create(7002L, 7002L, UserPricePreferences.defaults(), 10, NOW);
    }

    @Test
    void rejectsWalletDiscountOutsideTheSupportedRange() {
        UserProfileService service = new UserProfileService(
                mock(UserProfileStore.class), UserPricePreferences.defaults(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.updateWalletDiscountPercent(7001L, 7001L, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateWalletDiscountPercent(7001L, 7001L, 21))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
