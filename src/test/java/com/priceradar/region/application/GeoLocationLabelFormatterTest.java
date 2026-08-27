package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.GeoLocation;
import com.priceradar.region.domain.GeoLocationSource;
import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.region.domain.WildberriesLocationContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoLocationLabelFormatterTest {

    private final GeoLocationLabelFormatter formatter = new GeoLocationLabelFormatter();

    @Test
    void buildsOneConsistentHumanReadableLabel() {
        assertThat(formatter.format(candidate("Москва", "Москва", null)))
                .isEqualTo("Москва");
        assertThat(formatter.format(candidate("Киров", "Кировская область", null)))
                .isEqualTo("Киров, Кировская область");
        assertThat(formatter.format(candidate(
                "Лесной", "Кировская область", "Верхнекамский район"
        ))).isEqualTo("Лесной, Кировская область, Верхнекамский район");
    }

    @Test
    void settlementLengthMatchesPriceContextBoundary() {
        String maximumLengthName = "а".repeat(100);
        GeoCandidate candidate = candidate(maximumLengthName, null, null);
        ResolvedLocation resolved = new ResolvedLocation(
                new GeoLocation(UUID.randomUUID(), candidate, GeoLocationSource.NOMINATIM,
                        Instant.parse("2026-08-27T10:00:00Z")),
                new WildberriesLocationContext(1L, 30, Instant.parse("2026-08-27T10:00:00Z"))
        );

        assertThat(resolved.toPriceContext().getCityName()).isEqualTo(maximumLengthName);
        assertThatThrownBy(() -> candidate("а".repeat(101), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GeoCandidate candidate(String settlement, String region, String district) {
        return new GeoCandidate(
                settlement, region, district, "Россия",
                new BigDecimal("55.75"), new BigDecimal("37.61"),
                "node", UUID.randomUUID().toString(), "place", "city", 0.8, 16
        );
    }
}
