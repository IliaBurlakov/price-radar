package com.priceradar.marketplace.wildberries;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.marketplace.application.ProviderAccessCoordinator;
import com.priceradar.region.application.WildberriesGeoResult;
import com.priceradar.region.application.GeoLocationLabelFormatter;
import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.testsupport.LocalHttpStub;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WildberriesGeoHttpProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-27T09:00:00Z");

    @Test
    void parsesDestAndSppRegardlessOfXinfoParameterOrder() throws Exception {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/get-geo-info", 200,
                    "{\"xinfo\":\"spp=30&curr=rub&dest=1259570991&appType=1\"}");
            ProviderAccessCoordinator coordinator = mock(ProviderAccessCoordinator.class);

            WildberriesGeoResult result = provider(stub, coordinator).resolve(candidate());

            assertThat(result.getContext()).get()
                    .satisfies(context -> {
                        assertThat(context.getDestination()).isEqualTo(1259570991L);
                        assertThat(context.getSpp()).isEqualTo(30);
                        assertThat(context.getResolvedAt()).isEqualTo(NOW);
                    });
            verify(coordinator).awaitTurn();
            verify(coordinator).completeAfter(Duration.ZERO);
            assertThat(java.net.URLDecoder.decode(
                    stub.lastRequestUri().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8
            )).contains(
                    "latitude=55.7558",
                    "longitude=37.6173",
                    "address=Москва, Россия"
            );
        }
    }

    @Test
    void rejectsMalformedOrIncompleteWildberriesContext() {
        for (String body : new String[]{
                "{}", "{\"xinfo\":null}", "{\"xinfo\":\"spp=30\"}",
                "{\"xinfo\":\"dest=not-a-number&spp=30\"}",
                "{\"xinfo\":\"dest=1&spp=invalid\"}", "not-json"
        }) {
            try (LocalHttpStub stub = LocalHttpStub.start()) {
                stub.stub("/get-geo-info", 200, body);
                assertThat(provider(stub, mock(ProviderAccessCoordinator.class))
                        .resolve(candidate()).isSuccess()).isFalse();
            }
        }
    }

    @Test
    void containsNonSuccessfulHttpResponse() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/get-geo-info", 503, "{}");

            assertThat(provider(stub, mock(ProviderAccessCoordinator.class))
                    .resolve(candidate()).isSuccess()).isFalse();
        }
    }

    private WildberriesGeoHttpProvider provider(
            LocalHttpStub stub,
            ProviderAccessCoordinator coordinator
    ) {
        return new WildberriesGeoHttpProvider(
                HttpClient.newHttpClient(), new ObjectMapper(), coordinator,
                stub.baseUri().resolve("/get-geo-info"), Duration.ofSeconds(2), 64 * 1024,
                Clock.fixed(NOW, ZoneOffset.UTC), new GeoLocationLabelFormatter()
        );
    }

    private GeoCandidate candidate() {
        return new GeoCandidate(
                "Москва", "Москва", null, "Россия",
                new BigDecimal("55.7558"), new BigDecimal("37.6173"),
                "relation", "2555133", "place", "city", 0.9, 16
        );
    }
}
