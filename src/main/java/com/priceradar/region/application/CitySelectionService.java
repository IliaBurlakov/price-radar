package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.GeoLocation;
import com.priceradar.region.domain.GeoTextNormalizer;
import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CitySelectionService {

    private final GeocodingProvider geocodingProvider;
    private final WildberriesGeoProvider wildberriesGeoProvider;
    private final GeoLocationCatalog locationCatalog;
    private final PendingCitySelectionStore pendingStore;
    private final GeoCandidateDeduplicator deduplicator;
    private final UserRegionService userRegionService;
    private final SubscriptionStore subscriptionStore;
    private final Duration sessionTtl;

    public CitySelectionService(
            GeocodingProvider geocodingProvider,
            WildberriesGeoProvider wildberriesGeoProvider,
            GeoLocationCatalog locationCatalog,
            PendingCitySelectionStore pendingStore,
            GeoCandidateDeduplicator deduplicator,
            UserRegionService userRegionService,
            SubscriptionStore subscriptionStore,
            Duration sessionTtl
    ) {
        this.geocodingProvider = java.util.Objects.requireNonNull(geocodingProvider);
        this.wildberriesGeoProvider = java.util.Objects.requireNonNull(wildberriesGeoProvider);
        this.locationCatalog = java.util.Objects.requireNonNull(locationCatalog);
        this.pendingStore = java.util.Objects.requireNonNull(pendingStore);
        this.deduplicator = java.util.Objects.requireNonNull(deduplicator);
        this.userRegionService = java.util.Objects.requireNonNull(userRegionService);
        this.subscriptionStore = java.util.Objects.requireNonNull(subscriptionStore);
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("city selection session TTL must be positive");
        }
        this.sessionTtl = sessionTtl;
    }

    public void begin(UserProfile user, Instant now) {
        requireUserAndTime(user, now);
        pendingStore.replace(new PendingCitySelection(
                UUID.randomUUID(), user.getId(), List.of(), now, now.plus(sessionTtl)
        ), now);
    }

    public boolean hasPending(UserProfile user, Instant now) {
        requireUserAndTime(user, now);
        return pendingStore.findByUserId(user.getId(), now).isPresent();
    }

    public CitySelectionResult search(UserProfile user, String query, Instant now) {
        requireUserAndTime(user, now);
        pendingStore.remove(user.getId());
        String normalizedQuery = GeoTextNormalizer.normalize(query);
        if (normalizedQuery.isBlank() || normalizedQuery.codePointCount(0, normalizedQuery.length()) > 160) {
            return CitySelectionResult.status(CitySelectionResult.Status.NOT_FOUND);
        }
        long activeSubscriptions = user.isRegionSelected() ? subscriptionStore.countActive(user.getId()) : 0;
        if (activeSubscriptions > 0) return CitySelectionResult.blocked(activeSubscriptions);
        storeOptions(user, List.of(), now);

        Optional<List<GeoLocation>> cachedSearch = locationCatalog.findCompletedSearch(normalizedQuery);
        if (cachedSearch.isPresent()) {
            List<GeoCandidate> candidates = cachedSearch.orElseThrow().stream()
                    .map(GeoLocation::toCandidate)
                    .toList();
            if (candidates.isEmpty()) return CitySelectionResult.status(CitySelectionResult.Status.NOT_FOUND);
            if (candidates.size() == 1) {
                return resolveAndApplyFromSearch(user, candidates.getFirst(), now);
            }
            storeOptions(user, candidates, now);
            return CitySelectionResult.options(candidates);
        }

        GeocodingResult geocoding = geocodingProvider.search(query.trim());
        if (!geocoding.isSuccess()) {
            return CitySelectionResult.status(CitySelectionResult.Status.GEOCODING_UNAVAILABLE);
        }
        List<GeoCandidate> transientCandidates = deduplicator.deduplicate(geocoding.getCandidates());
        List<GeoCandidate> candidates = locationCatalog.saveCompletedSearch(
                        normalizedQuery, transientCandidates, now
                ).stream()
                .map(GeoLocation::toCandidate)
                .toList();
        if (candidates.isEmpty()) return CitySelectionResult.status(CitySelectionResult.Status.NOT_FOUND);
        if (candidates.size() == 1) return resolveAndApplyFromSearch(user, candidates.getFirst(), now);
        storeOptions(user, candidates, now);
        return CitySelectionResult.options(candidates);
    }

    public CitySelectionResult choose(UserProfile user, int oneBasedNumber, Instant now) {
        requireUserAndTime(user, now);
        Optional<PendingCitySelection> pending = pendingStore.findByUserId(user.getId(), now);
        if (pending.isEmpty() || pending.orElseThrow().getCandidates().isEmpty()) {
            return CitySelectionResult.status(CitySelectionResult.Status.SESSION_EXPIRED);
        }
        List<GeoCandidate> candidates = pending.orElseThrow().getCandidates();
        if (oneBasedNumber < 1 || oneBasedNumber > candidates.size()) {
            return CitySelectionResult.status(CitySelectionResult.Status.INVALID_NUMBER);
        }
        CitySelectionResult result = resolveAndApply(user, candidates.get(oneBasedNumber - 1), now);
        if (result.getStatus() == CitySelectionResult.Status.SELECTED
                || result.getStatus() == CitySelectionResult.Status.CHANGED
                || result.getStatus() == CitySelectionResult.Status.UNCHANGED) {
            pendingStore.remove(user.getId());
        }
        return result;
    }

    public Optional<PendingCitySelection> pending(UserProfile user, Instant now) {
        requireUserAndTime(user, now);
        return pendingStore.findByUserId(user.getId(), now);
    }

    public void cancel(UserProfile user) {
        if (user == null) throw new IllegalArgumentException("user must not be null");
        pendingStore.remove(user.getId());
    }

    private CitySelectionResult resolveAndApply(UserProfile user, GeoCandidate candidate, Instant now) {
        Optional<ResolvedLocation> cached = locationCatalog.findResolvedByIdentityKey(candidate.identityKey());
        if (cached.isPresent()) return apply(user, cached.orElseThrow(), now);
        WildberriesGeoResult resolved = wildberriesGeoProvider.resolve(candidate);
        if (!resolved.isSuccess()) {
            return CitySelectionResult.status(CitySelectionResult.Status.WILDBERRIES_UNAVAILABLE);
        }
        ResolvedLocation location = locationCatalog.saveResolved(
                candidate, resolved.getContext().orElseThrow(), now
        );
        return apply(user, location, now);
    }

    private CitySelectionResult resolveAndApplyFromSearch(
            UserProfile user,
            GeoCandidate candidate,
            Instant now
    ) {
        CitySelectionResult result = resolveAndApply(user, candidate, now);
        removePendingAfterSuccess(user, result);
        return result;
    }

    private void removePendingAfterSuccess(UserProfile user, CitySelectionResult result) {
        if (result.getStatus() == CitySelectionResult.Status.SELECTED
                || result.getStatus() == CitySelectionResult.Status.CHANGED
                || result.getStatus() == CitySelectionResult.Status.UNCHANGED) {
            pendingStore.remove(user.getId());
        }
    }

    private CitySelectionResult apply(UserProfile user, ResolvedLocation location, Instant now) {
        RegionChangeResult change = userRegionService.changeLocation(user.getId(), location, now);
        return switch (change.getStatus()) {
            case SELECTED -> CitySelectionResult.selected(CitySelectionResult.Status.SELECTED, location);
            case CHANGED -> CitySelectionResult.selected(CitySelectionResult.Status.CHANGED, location);
            case UNCHANGED -> CitySelectionResult.selected(CitySelectionResult.Status.UNCHANGED, location);
            case ACTIVE_SUBSCRIPTIONS -> CitySelectionResult.blocked(change.getActiveSubscriptions());
            case USER_NOT_FOUND -> CitySelectionResult.status(CitySelectionResult.Status.USER_NOT_FOUND);
        };
    }

    private void storeOptions(UserProfile user, List<GeoCandidate> candidates, Instant now) {
        pendingStore.replace(new PendingCitySelection(
                UUID.randomUUID(), user.getId(), candidates, now, now.plus(sessionTtl)
        ), now);
    }

    private void requireUserAndTime(UserProfile user, Instant now) {
        if (user == null || now == null) throw new IllegalArgumentException("user and time must not be null");
    }
}
