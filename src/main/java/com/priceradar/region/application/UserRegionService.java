package com.priceradar.region.application;

import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileStore;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class UserRegionService {

    private final UserProfileStore userProfileStore;
    private final SubscriptionStore subscriptionStore;

    public UserRegionService(
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore
    ) {
        this.userProfileStore = Objects.requireNonNull(userProfileStore);
        this.subscriptionStore = Objects.requireNonNull(subscriptionStore);
    }

    @Transactional
    public RegionChangeResult changeLocation(
            UUID userId,
            ResolvedLocation requestedLocation,
            Instant now
    ) {
        if (userId == null || requestedLocation == null || now == null) {
            throw new IllegalArgumentException("region change fields must not be null");
        }
        UserProfile user = userProfileStore.findByIdAndLock(userId).orElse(null);
        if (user == null) {
            return RegionChangeResult.failed(RegionChangeResult.Status.USER_NOT_FOUND);
        }
        boolean initialSelection = !user.isRegionSelected();
        if (!initialSelection && user.getLocation().orElseThrow().getId()
                .equals(requestedLocation.getLocation().getId())) {
            return RegionChangeResult.withLocation(RegionChangeResult.Status.UNCHANGED, requestedLocation);
        }
        if (!initialSelection) {
            long active = subscriptionStore.countActive(userId);
            if (active > 0) {
                return RegionChangeResult.blocked(requestedLocation, active);
            }
        }
        UserProfile updated = userProfileStore.updateLocation(user, requestedLocation, now);
        RegionChangeResult.Status status = initialSelection
                ? RegionChangeResult.Status.SELECTED
                : RegionChangeResult.Status.CHANGED;
        return RegionChangeResult.withLocation(status, updated.getResolvedLocation().orElseThrow());
    }
}
