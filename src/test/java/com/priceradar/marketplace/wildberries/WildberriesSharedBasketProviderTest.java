package com.priceradar.marketplace.wildberries;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.marketplace.application.ProviderAccessCoordinator;
import com.priceradar.marketplace.application.ProviderCooldownStore;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.sharedbasket.application.SharedBasketFailureCode;
import com.priceradar.sharedbasket.application.SharedBasketItem;
import com.priceradar.testsupport.LocalHttpStub;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WildberriesSharedBasketProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");
    private static final String SHARE_ID = "abc123def4";

    @Test
    void fetchesBasketAndResolvesOnlyExactChrtVariantsInOneBatch() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/share-basket/api/v1/basket/" + SHARE_ID, 200, """
                    {"items":[
                      {"nmId":35989562,"chrtId":75115776,"quantity":2},
                      {"nmId":584886456,"chrtId":799537776,"quantity":1},
                      {"nmId":35989562,"chrtId":99999999,"quantity":1}
                    ]}
                    """);
            stub.stub("/cards/v4/list", 200, fixture("wildberries/shared-basket/cards-v4-list.json"));
            WildberriesSharedBasketProvider provider = provider(stub, 1);

            var basket = provider.fetch(SHARE_ID).getBasket().orElseThrow();
            var resolution = provider.resolveExact(
                    basket.getItems(),
                    new PriceContext("Moscow", 1259570991L, 30)
            );

            assertThat(resolution.isSuccess()).isTrue();
            assertThat(resolution.getResolvedItems()).hasSize(2);
            assertThat(resolution.getSkippedItems()).isEqualTo(1);
            assertThat(resolution.getResolvedItems().getFirst().getVariant().getVariantKey())
                    .isEqualTo("SIZE:75115776");
            assertThat(resolution.getResolvedItems().getFirst().getVariant().getDisplayName())
                    .contains("Size: XXL");
            assertThat(stub.requestCount()).isEqualTo(2);
        }
    }

    @Test
    void mapsNotFoundMalformedSchemaAndTemporaryFailuresWithoutRealNetwork() {
        try (LocalHttpStub notFound = LocalHttpStub.start()) {
            notFound.stub("/share-basket/api/v1/basket/" + SHARE_ID, 404, "{}");
            assertThat(provider(notFound, 1).fetch(SHARE_ID).getFailure().orElseThrow().getCode())
                    .isEqualTo(SharedBasketFailureCode.NOT_FOUND);
        }
        try (LocalHttpStub malformed = LocalHttpStub.start()) {
            malformed.stub("/share-basket/api/v1/basket/" + SHARE_ID, 200, "{broken");
            assertThat(provider(malformed, 1).fetch(SHARE_ID).getFailure().orElseThrow().getCode())
                    .isEqualTo(SharedBasketFailureCode.MALFORMED_RESPONSE);
        }
        try (LocalHttpStub schema = LocalHttpStub.start()) {
            schema.stub("/share-basket/api/v1/basket/" + SHARE_ID, 200, "{\"items\":[{\"nmId\":1}]}");
            assertThat(provider(schema, 1).fetch(SHARE_ID).getFailure().orElseThrow().getCode())
                    .isEqualTo(SharedBasketFailureCode.SCHEMA_VIOLATION);
        }
        try (LocalHttpStub unavailable = LocalHttpStub.start()) {
            unavailable.stub("/share-basket/api/v1/basket/" + SHARE_ID, 503, "{}");
            assertThat(provider(unavailable, 1).fetch(SHARE_ID).getFailure().orElseThrow().getCode())
                    .isEqualTo(SharedBasketFailureCode.TEMPORARILY_UNAVAILABLE);
        }
    }

    private WildberriesSharedBasketProvider provider(LocalHttpStub stub, int maxAttempts) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProviderAccessCoordinator coordinator = new ProviderAccessCoordinator(
                Duration.ZERO, clock, Marketplace.WILDBERRIES, new NoCooldownStore()
        );
        return new WildberriesSharedBasketProvider(
                HttpClient.newHttpClient(),
                stub.baseUri().resolve("/share-basket/api/v1/basket/"),
                stub.baseUri().resolve("/cards/v4/list"),
                new WildberriesSharedBasketMapper(new ObjectMapper()),
                new WildberriesCardMapper(), coordinator,
                Duration.ofSeconds(2), Duration.ofMillis(1), Duration.ofMillis(2),
                Duration.ofHours(1), maxAttempts, 2 * 1024 * 1024, clock
        );
    }

    private String fixture(String path) {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalArgumentException("Fixture not found: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class NoCooldownStore implements ProviderCooldownStore {
        @Override public Optional<Instant> findCooldownUntil(Marketplace marketplace) { return Optional.empty(); }
        @Override public void saveCooldownUntil(Marketplace marketplace, Instant cooldownUntil, Instant updatedAt) {}
    }
}
