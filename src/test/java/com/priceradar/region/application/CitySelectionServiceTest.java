package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.GeoLocation;
import com.priceradar.region.domain.GeoLocationSource;
import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.region.domain.WildberriesLocationContext;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CitySelectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");
    private final GeocodingProvider geocoding = mock(GeocodingProvider.class);
    private final WildberriesGeoProvider wildberries = mock(WildberriesGeoProvider.class);
    private final GeoLocationCatalog catalog = mock(GeoLocationCatalog.class);
    private final InMemoryPendingStore pending = new InMemoryPendingStore();
    private final UserRegionService regions = mock(UserRegionService.class);
    private final SubscriptionStore subscriptions = mock(SubscriptionStore.class);
    private final CitySelectionService service = new CitySelectionService(
            geocoding, wildberries, catalog, pending, new GeoCandidateDeduplicator(),
            regions, subscriptions, Duration.ofMinutes(15)
    );
    private final UserProfile user = new UserProfile(
            UUID.randomUUID(), 1001L, 1001L, null, UserPricePreferences.defaults()
    );

    @BeforeEach
    void defaults() {
        when(catalog.findCompletedSearch(any())).thenReturn(Optional.empty());
        when(catalog.saveCompletedSearch(any(), any(), any())).thenAnswer(invocation -> {
            List<GeoCandidate> candidates = invocation.getArgument(1);
            Instant completedAt = invocation.getArgument(2);
            return candidates.stream()
                    .map(candidate -> new GeoLocation(
                            UUID.randomUUID(), candidate, GeoLocationSource.NOMINATIM, completedAt
                    ))
                    .toList();
        });
    }

    @Test
    void oneLogicalCandidateIsResolvedAndSelectedImmediately() {
        GeoCandidate first = candidate("Москва", "Москва", 55.7558, 37.6173, "boundary", 0.7);
        GeoCandidate better = candidate("Москва", "Москва", 55.7560, 37.6175, "city", 0.9);
        ResolvedLocation location = resolved(better);
        when(geocoding.search("Москва")).thenReturn(GeocodingResult.success(List.of(first, better)));
        when(wildberries.resolve(better)).thenReturn(WildberriesGeoResult.success(
                location.getWildberriesContext()
        ));
        when(catalog.saveResolved(any(), any(), any())).thenReturn(location);
        when(regions.changeLocation(user.getId(), location, NOW))
                .thenReturn(RegionChangeResult.withLocation(RegionChangeResult.Status.SELECTED, location));

        CitySelectionResult result = service.search(user, " Москва ", NOW);

        assertThat(result.getStatus()).isEqualTo(CitySelectionResult.Status.SELECTED);
        assertThat(pending.findByUserId(user.getId(), NOW)).isEmpty();
    }

    @Test
    void ambiguousSearchPersistsOptionsAndInvalidNumberKeepsThem() {
        GeoCandidate first = candidate("Киров", "Кировская область", 58.60, 49.66, "city", 0.8);
        GeoCandidate second = candidate("Киров", "Калужская область", 54.08, 34.30, "town", 0.5);
        when(geocoding.search("Киров")).thenReturn(GeocodingResult.success(List.of(first, second)));

        assertThat(service.search(user, "Киров", NOW).getStatus())
                .isEqualTo(CitySelectionResult.Status.OPTIONS);
        assertThat(service.choose(user, 7, NOW.plusSeconds(1)).getStatus())
                .isEqualTo(CitySelectionResult.Status.INVALID_NUMBER);
        assertThat(pending.findByUserId(user.getId(), NOW.plusSeconds(1)).orElseThrow()
                .getCandidates()).containsExactly(first, second);
    }

    @Test
    void newTextReplacesOldOptionsAndExpiredOrForeignSessionCannotBeApplied() {
        GeoCandidate kirov = candidate("Киров", "Кировская область", 58.60, 49.66, "city", 0.8);
        GeoCandidate otherKirov = candidate("Киров", "Калужская область", 54.08, 34.30, "town", 0.5);
        when(geocoding.search("Киров")).thenReturn(GeocodingResult.success(List.of(kirov, otherKirov)));
        when(geocoding.search("Томск")).thenReturn(GeocodingResult.success(List.of()));
        service.search(user, "Киров", NOW);

        assertThat(service.search(user, "Томск", NOW.plusSeconds(2)).getStatus())
                .isEqualTo(CitySelectionResult.Status.NOT_FOUND);
        assertThat(pending.findByUserId(user.getId(), NOW.plusSeconds(2)).orElseThrow()
                .getCandidates()).isEmpty();

        service.begin(user, NOW);
        assertThat(service.choose(user, 1, NOW.plus(Duration.ofMinutes(16))).getStatus())
                .isEqualTo(CitySelectionResult.Status.SESSION_EXPIRED);
        UserProfile anotherUser = new UserProfile(
                UUID.randomUUID(), 2002L, 2002L, null, UserPricePreferences.defaults()
        );
        assertThat(service.choose(anotherUser, 1, NOW).getStatus())
                .isEqualTo(CitySelectionResult.Status.SESSION_EXPIRED);
    }

    @Test
    void cachedResolvedLocationAvoidsBothExternalProviders() {
        ResolvedLocation location = resolved(candidate("Бердск", "Новосибирская область",
                54.75, 83.10, "town", 0.5));
        when(catalog.findCompletedSearch("бердск"))
                .thenReturn(Optional.of(List.of(location.getLocation())));
        when(catalog.findResolvedByIdentityKey(location.getLocation().getIdentityKey()))
                .thenReturn(Optional.of(location));
        when(regions.changeLocation(user.getId(), location, NOW))
                .thenReturn(RegionChangeResult.withLocation(RegionChangeResult.Status.SELECTED, location));

        assertThat(service.search(user, "БЕРДСК", NOW).getStatus())
                .isEqualTo(CitySelectionResult.Status.SELECTED);
        verify(geocoding, never()).search(any());
        verify(wildberries, never()).resolve(any());
    }

    @Test
    void completedAmbiguousSearchKeepsAllKirovCandidatesForTheNextUser() {
        GeoCandidate first = candidate("Киров", "Кировская область", 58.60, 49.66, "city", 0.8);
        GeoCandidate second = candidate("Киров", "Калужская область", 54.08, 34.30, "town", 0.5);
        List<GeoLocation> cached = List.of(
                resolved(first).getLocation(), resolved(second).getLocation()
        );
        when(geocoding.search("Киров")).thenReturn(GeocodingResult.success(List.of(first, second)));
        when(catalog.findCompletedSearch("киров"))
                .thenReturn(Optional.empty(), Optional.of(cached));
        ResolvedLocation selected = new ResolvedLocation(
                cached.getFirst(), new WildberriesLocationContext(1259570991L, 30, NOW)
        );
        when(wildberries.resolve(first)).thenReturn(WildberriesGeoResult.success(
                selected.getWildberriesContext()
        ));
        when(catalog.saveResolved(first, selected.getWildberriesContext(), NOW))
                .thenReturn(selected);
        when(regions.changeLocation(user.getId(), selected, NOW))
                .thenReturn(RegionChangeResult.withLocation(
                        RegionChangeResult.Status.SELECTED, selected
                ));

        assertThat(service.search(user, "Киров", NOW).getStatus())
                .isEqualTo(CitySelectionResult.Status.OPTIONS);
        assertThat(service.choose(user, 1, NOW).getStatus())
                .isEqualTo(CitySelectionResult.Status.SELECTED);
        UserProfile nextUser = new UserProfile(
                UUID.randomUUID(), 2002L, 2002L, null, UserPricePreferences.defaults()
        );
        assertThat(service.search(nextUser, "Киров", NOW.plusSeconds(1)).getCandidates())
                .extracting(GeoCandidate::getRegionName)
                .containsExactly(Optional.of("Кировская область"), Optional.of("Калужская область"));
        verify(geocoding).search("Киров");
    }

    @Test
    void usesCanonicalCandidatesReturnedByCompletedSearchPersistence() {
        GeoCandidate transientFirst = candidate(
                "Киров", "Кировская область", 58.60, 49.66, "city", 0.8
        );
        GeoCandidate transientSecond = candidate(
                "Киров", "Калужская область", 54.08, 34.30, "town", 0.5
        );
        GeoCandidate canonicalFirst = candidate(
                "Киров", "Калужская область", 54.081, 34.301, "town", 0.6
        );
        GeoCandidate canonicalSecond = candidate(
                "Киров", "Кировская область", 58.601, 49.661, "city", 0.9
        );
        when(geocoding.search("Киров")).thenReturn(GeocodingResult.success(
                List.of(transientFirst, transientSecond)
        ));
        doReturn(List.of(
                resolved(canonicalFirst).getLocation(), resolved(canonicalSecond).getLocation()
        )).when(catalog).saveCompletedSearch(any(), any(), any());

        CitySelectionResult result = service.search(user, "Киров", NOW);

        assertThat(result.getStatus()).isEqualTo(CitySelectionResult.Status.OPTIONS);
        assertThat(result.getCandidates()).containsExactly(canonicalFirst, canonicalSecond);
        assertThat(pending.findByUserId(user.getId(), NOW).orElseThrow().getCandidates())
                .containsExactly(canonicalFirst, canonicalSecond);
    }

    @Test
    void externalFailureNeverAppliesPartialLocation() {
        GeoCandidate tomsk = candidate("Томск", "Томская область", 56.49, 84.95, "city", 0.8);
        when(geocoding.search("Томск")).thenReturn(GeocodingResult.success(List.of(tomsk)));
        when(wildberries.resolve(tomsk)).thenReturn(WildberriesGeoResult.temporarilyUnavailable());

        assertThat(service.search(user, "Томск", NOW).getStatus())
                .isEqualTo(CitySelectionResult.Status.WILDBERRIES_UNAVAILABLE);
        assertThat(pending.findByUserId(user.getId(), NOW).orElseThrow().getCandidates()).isEmpty();
        verify(catalog, never()).saveResolved(any(), any(), any());
        verify(regions, never()).changeLocation(any(), any(), any());
    }

    private GeoCandidate candidate(
            String name, String region, double latitude, double longitude,
            String type, double importance
    ) {
        return new GeoCandidate(
                name, region, null, "Россия", BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
                "relation", name + region, "place", type, importance, 16
        );
    }

    private ResolvedLocation resolved(GeoCandidate candidate) {
        return new ResolvedLocation(
                new GeoLocation(UUID.randomUUID(), candidate, GeoLocationSource.NOMINATIM, NOW),
                new WildberriesLocationContext(1259570991L, 30, NOW)
        );
    }

    private static final class InMemoryPendingStore implements PendingCitySelectionStore {
        private final Map<UUID, PendingCitySelection> values = new HashMap<>();

        @Override
        public PendingCitySelection replace(PendingCitySelection selection, Instant now) {
            values.put(selection.getUserId(), selection);
            return selection;
        }

        @Override
        public Optional<PendingCitySelection> findByUserId(UUID userId, Instant now) {
            PendingCitySelection value = values.get(userId);
            if (value == null || !now.isBefore(value.getExpiresAt())) {
                values.remove(userId);
                return Optional.empty();
            }
            return Optional.of(value);
        }

        @Override
        public void remove(UUID userId) {
            values.remove(userId);
        }
    }
}
