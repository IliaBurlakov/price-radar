package com.priceradar.region.infrastructure.geocoding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.region.application.GeocodingResult;
import com.priceradar.testsupport.LocalHttpStub;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NominatimGeocodingProviderTest {

    @Test
    void mapsRussianSettlementsAndFiltersRoadsAndForeignResults() {
        String response = """
                [
                  {"osm_type":"relation","osm_id":1,"category":"place","type":"city",
                   "lat":"55.7558","lon":"37.6173","importance":0.9,"place_rank":16,
                   "address":{"city":"Москва","state":"Москва","country":"Россия","country_code":"ru"}},
                  {"osm_type":"way","osm_id":2,"category":"place","type":"town",
                   "lat":"65.99","lon":"57.55","address":{"town":"Усинск","road":"87К-134",
                   "state":"Республика Коми","country":"Россия","country_code":"ru"}},
                  {"osm_type":"node","osm_id":3,"category":"place","type":"village",
                   "lat":"55.0","lon":"82.0","address":{"village":"Берёзовка",
                   "state":"Новосибирская область","country":"Россия","country_code":"ru"}},
                  {"osm_type":"node","osm_id":4,"category":"place","type":"city",
                   "lat":"53.9","lon":"27.5","address":{"city":"Минск","country":"Беларусь","country_code":"by"}},
                  {"osm_type":"node","osm_id":5,"category":"place","type":"hamlet",
                   "lat":"57.0","lon":"50.0","address":{"hamlet":"Лесной","municipality":"Лесное поселение",
                   "state":"Кировская область","country":"Россия","country_code":"ru"}},
                  {"osm_type":"node","osm_id":6,"category":"place","type":"town",
                   "lat":"56.0","lon":"51.0","address":{"town":"Малмыж",
                   "state":"Кировская область","country":"Россия","country_code":"ru"}}
                ]
                """;
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/search", 200, response);

            GeocodingResult result = provider(stub.baseUri().resolve("/search")).search("Москва");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCandidates()).extracting(candidate -> candidate.getSettlementName())
                    .containsExactly("Москва", "Берёзовка", "Лесной", "Малмыж");
            assertThat(stub.requestCount()).isEqualTo(1);
            assertThat(stub.lastRequestUri().getRawQuery())
                    .contains("featureType=settlement", "addressdetails=1", "countrycodes=ru");
        }
    }

    @Test
    void treatsEmptyArrayAsSuccessfulSearchWithoutCandidates() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/search", 200, "[]");

            GeocodingResult result = provider(stub.baseUri().resolve("/search")).search("Неизвестно");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCandidates()).isEmpty();
        }
    }

    @Test
    void containsExternalProtocolFailures() throws Exception {
        try (LocalHttpStub nonSuccess = LocalHttpStub.start();
             LocalHttpStub malformed = LocalHttpStub.start()) {
            nonSuccess.stub("/search", 503, "{}");
            malformed.stub("/search", 200, "not-json");

            assertThat(provider(nonSuccess.baseUri().resolve("/search")).search("Томск").isSuccess())
                    .isFalse();
            assertThat(provider(malformed.baseUri().resolve("/search")).search("Томск").isSuccess())
                    .isFalse();
        }

        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("timeout"))
                .thenThrow(new IOException("network"));
        NominatimGeocodingProvider provider = provider(client, URI.create("http://localhost/search"));

        assertThat(provider.search("Томск").isSuccess()).isFalse();
        assertThat(provider.search("Томск").isSuccess()).isFalse();
    }

    private NominatimGeocodingProvider provider(URI endpoint) {
        return provider(HttpClient.newHttpClient(), endpoint);
    }

    private NominatimGeocodingProvider provider(HttpClient client, URI endpoint) {
        NominatimProperties properties = new NominatimProperties();
        properties.setBaseUrl(endpoint);
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setMinRequestInterval(Duration.ZERO);
        return new NominatimGeocodingProvider(
                client,
                new ObjectMapper(),
                new NominatimRequestCoordinator(Duration.ZERO, Clock.systemUTC()),
                properties
        );
    }
}
