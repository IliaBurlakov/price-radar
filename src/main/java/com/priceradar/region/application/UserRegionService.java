package com.priceradar.region.application;

import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.region.domain.MarketplaceRegionCode;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileStore;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class UserRegionService {

    private final MarketplaceRegionStore regionStore;
    private final UserProfileStore userProfileStore;
    private final SubscriptionStore subscriptionStore;

    public UserRegionService(
            MarketplaceRegionStore regionStore,
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore
    ) {
        this.regionStore = Objects.requireNonNull(regionStore);
        this.userProfileStore = Objects.requireNonNull(userProfileStore);
        this.subscriptionStore = Objects.requireNonNull(subscriptionStore);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceRegion> listEnabled() {
        return regionStore.findEnabled();
    }

    @Transactional
    public RegionChangeResult changeRegion(
            UUID userId,
            MarketplaceRegionCode requestedCode,
            Instant now
    ) {
        if (userId == null || requestedCode == null || now == null) {
            throw new IllegalArgumentException("region change fields must not be null");
        }
        UserProfile user = userProfileStore.findByIdAndLock(userId).orElse(null);
        if (user == null) {
            return RegionChangeResult.failed(RegionChangeResult.Status.USER_NOT_FOUND);
        }
        MarketplaceRegion requested = regionStore.findByCode(requestedCode)
                .filter(MarketplaceRegion::isEnabled)
                .orElse(null);
        if (requested == null) {
            return RegionChangeResult.failed(RegionChangeResult.Status.REGION_NOT_FOUND);
        }
        boolean initialSelection = !user.isRegionSelected();
        if (!initialSelection && user.getRegion().getCode() == requestedCode) {
            return RegionChangeResult.withRegion(RegionChangeResult.Status.UNCHANGED, requested);
        }
        if (user.getRegion().getCode() != requestedCode) {
            long active = subscriptionStore.countActive(userId);
            if (active > 0) {
                return RegionChangeResult.blocked(requested, active);
            }
        }
        UserProfile updated = userProfileStore.updateRegion(user, requested, now);
        RegionChangeResult.Status status = initialSelection
                ? RegionChangeResult.Status.SELECTED
                : RegionChangeResult.Status.CHANGED;
        return RegionChangeResult.withRegion(status, updated.getRegion());
    }
}
