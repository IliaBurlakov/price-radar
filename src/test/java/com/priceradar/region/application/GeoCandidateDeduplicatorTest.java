package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeoCandidateDeduplicatorTest {

    private final GeoCandidateDeduplicator deduplicator = new GeoCandidateDeduplicator();

    @Test
    void mergesDuplicateRepresentationsOfMoscowAndKeepsTheBetterPlaceResult() {
        GeoCandidate boundary = candidate("Москва", "Москва", null, 55.750, 37.610,
                "boundary", "administrative", 0.70);
        GeoCandidate city = candidate("Москва", "Москва", null, 55.756, 37.617,
                "place", "city", 0.90);

        assertThat(deduplicator.deduplicate(List.of(boundary, city)))
                .containsExactly(city);
    }

    @Test
    void keepsSameNamedSettlementsWhenTheyAreActuallyDifferent() {
        GeoCandidate kirovRegion = candidate("Киров", "Кировская область", null,
                58.603, 49.668, "place", "city", 0.80);
        GeoCandidate kalugaRegion = candidate("Киров", "Калужская область", null,
                54.079, 34.307, "place", "town", 0.55);
        GeoCandidate distantSameRegion = candidate("Киров", "Кировская область", "Другой район",
                57.000, 47.000, "place", "village", 0.30);

        assertThat(deduplicator.deduplicate(List.of(kirovRegion, kalugaRegion, distantSameRegion)))
                .containsExactly(kirovRegion, kalugaRegion, distantSameRegion);
    }

    @Test
    void doesNotMergeCloseSettlementsWhenStructuredDistrictsDiffer() {
        GeoCandidate first = candidate("Октябрьский", "Московская область", "Люберцы",
                55.61, 37.97, "place", "settlement", 0.4);
        GeoCandidate second = candidate("Октябрьский", "Московская область", "Истра",
                55.62, 37.98, "place", "village", 0.3);

        assertThat(deduplicator.deduplicate(List.of(first, second)))
                .containsExactly(first, second);
    }

    private GeoCandidate candidate(
            String name, String region, String district, double latitude, double longitude,
            String category, String type, double importance
    ) {
        return new GeoCandidate(
                name, region, district, "Россия",
                BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
                "relation", name + latitude, category, type, importance, 16
        );
    }
}
