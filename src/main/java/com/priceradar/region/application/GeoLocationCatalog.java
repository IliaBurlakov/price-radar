package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.GeoLocation;
import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.region.domain.WildberriesLocationContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeoLocationCatalog {

    Optional<List<GeoLocation>> findCompletedSearch(String normalizedQuery);

    List<GeoLocation> saveCompletedSearch(
            String normalizedQuery,
            List<GeoCandidate> candidates,
            Instant completedAt
    );

    Optional<ResolvedLocation> findResolvedByIdentityKey(String identityKey);

    Optional<ResolvedLocation> findById(UUID locationId);

    ResolvedLocation saveResolved(
            GeoCandidate candidate,
            WildberriesLocationContext context,
            Instant createdAt
    );
}
