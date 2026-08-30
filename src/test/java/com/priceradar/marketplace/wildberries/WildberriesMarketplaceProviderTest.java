package com.priceradar.marketplace.wildberries;

import com.priceradar.marketplace.application.MarketplaceProductRequest;
import com.priceradar.marketplace.application.MarketplaceProviderFailure;
import com.priceradar.marketplace.application.MarketplaceProviderFailureCode;
import com.priceradar.marketplace.application.MarketplaceProviderResult;
import com.priceradar.marketplace.application.ProviderAccessCoordinator;
import com.priceradar.marketplace.application.ProviderCooldownStore;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.domain.PriceContext;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WildberriesMarketplaceProviderTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final PriceContext MOSCOW = new PriceContext("Moscow", 1259570991L, 30);

    @Test
    void returnsNormalizedProductForSuccessfulResponse() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub(
                    "/cards/v4/detail",
                    200,
                    fixture("wildberries/card-detail/regular-card-v4-detail.json")
            );
            TestCooldownStore cooldownStore = new TestCooldownStore();
            WildberriesMarketplaceProvider provider = provider(stub, cooldownStore);

            MarketplaceProviderResult result = provider.resolveProduct(request());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getObservedAt()).contains(NOW);
            assertThat(result.getProduct()).get()
                    .extracting(product -> product.getExternalProductId())
                    .isEqualTo("123456789");
            assertThat(stub.requestCount()).isEqualTo(1);
        }
    }

    @Test
    void keepsRateLimitedResultWhenCooldownPersistenceFails() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub(
                    "/cards/v4/detail",
                    429,
                    "{}",
                    Map.of("Retry-After", "120")
            );
            TestCooldownStore cooldownStore = new TestCooldownStore();
            cooldownStore.failOnSave = true;
            WildberriesMarketplaceProvider provider = provider(stub, cooldownStore);

            MarketplaceProviderResult result = provider.resolveProduct(request());

            MarketplaceProviderFailure failure = result.getFailure().orElseThrow();
            assertThat(failure.getCode()).isEqualTo(MarketplaceProviderFailureCode.RATE_LIMITED);
            assertThat(failure.getRetryNotBefore()).contains(NOW.plus(Duration.ofMinutes(15)));
            assertThat(stub.requestCount()).isEqualTo(1);
        }
    }

    @Test
    void blocksRequestWithTypedFailureWhenCooldownStateCannotBeRead() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub(
                    "/cards/v4/detail",
                    200,
                    fixture("wildberries/card-detail/regular-card-v4-detail.json")
            );
            TestCooldownStore cooldownStore = new TestCooldownStore();
            cooldownStore.failOnRead = true;
            WildberriesMarketplaceProvider provider = provider(stub, cooldownStore);

            MarketplaceProviderResult result = provider.resolveProduct(request());

            MarketplaceProviderFailure failure = result.getFailure().orElseThrow();
            assertThat(failure.getCode()).isEqualTo(MarketplaceProviderFailureCode.COOLDOWN_ACTIVE);
            assertThat(failure.getRetryNotBefore()).contains(NOW.plus(Duration.ofMinutes(1)));
            assertThat(stub.requestCount()).isZero();
        }
    }

    @Test
    void honorsRetryAfterForServiceUnavailableWithoutImmediateRetry() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub(
                    "/cards/v4/detail",
                    503,
                    "{}",
                    Map.of("Retry-After", "120")
            );
            TestCooldownStore cooldownStore = new TestCooldownStore();
            WildberriesMarketplaceProvider provider = provider(stub, cooldownStore);

            MarketplaceProviderResult result = provider.resolveProduct(request());

            MarketplaceProviderFailure failure = result.getFailure().orElseThrow();
            assertThat(failure.getCode()).isEqualTo(MarketplaceProviderFailureCode.SERVER_ERROR);
            assertThat(failure.getRetryNotBefore()).contains(NOW.plusSeconds(120));
            assertThat(stub.requestCount()).isEqualTo(1);
        }
    }

    @Test
    void capsUntrustedRetryAfterValue() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub(
                    "/cards/v4/detail",
                    503,
                    "{}",
                    Map.of("Retry-After", String.valueOf(Long.MAX_VALUE))
            );
            WildberriesMarketplaceProvider provider = provider(stub, new TestCooldownStore());

            MarketplaceProviderFailure failure = provider.resolveProduct(request())
                    .getFailure()
                    .orElseThrow();

            assertThat(failure.getRetryNotBefore()).contains(NOW.plus(Duration.ofHours(24)));
            assertThat(stub.requestCount()).isEqualTo(1);
        }
    }

    @Test
    void activatesCooldownForInvalidProviderResponses() {
        assertInvalidResponseActivatesCooldown(
                "{not-json",
                MarketplaceProviderFailureCode.MALFORMED_RESPONSE
        );
        String duplicateSizeIds = """
                {
                  "data": {
                    "products": [{
                      "id": 123456789,
                      "sizes": [
                        {"optionId": 111, "stocks": [{"qty": 1}], "price": {"product": 10000}},
                        {"optionId": 111, "stocks": [{"qty": 1}], "price": {"product": 9000}}
                      ]
                    }]
                  }
                }
                """;

        assertInvalidResponseActivatesCooldown(
                duplicateSizeIds,
                MarketplaceProviderFailureCode.SCHEMA_VIOLATION
        );
    }

    private void assertInvalidResponseActivatesCooldown(
            String responseBody,
            MarketplaceProviderFailureCode expectedCode
    ) {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/cards/v4/detail", 200, responseBody);
            TestCooldownStore cooldownStore = new TestCooldownStore();
            WildberriesMarketplaceProvider provider = provider(stub, cooldownStore);

            MarketplaceProviderFailure failure = provider.resolveProduct(request())
                    .getFailure()
                    .orElseThrow();

            assertThat(failure.getCode()).isEqualTo(expectedCode);
            assertThat(failure.getRetryNotBefore()).contains(NOW.plus(Duration.ofMinutes(15)));
            assertThat(cooldownStore.cooldownUntil).isEqualTo(NOW.plus(Duration.ofMinutes(15)));

            MarketplaceProviderFailure blocked = provider.resolveProduct(request())
                    .getFailure()
                    .orElseThrow();
            assertThat(blocked.getCode()).isEqualTo(MarketplaceProviderFailureCode.COOLDOWN_ACTIVE);
            assertThat(stub.requestCount()).isEqualTo(1);
        }
    }

    private WildberriesMarketplaceProvider provider(
            LocalHttpStub stub,
            ProviderCooldownStore cooldownStore
    ) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProviderAccessCoordinator coordinator = new ProviderAccessCoordinator(
                Duration.ZERO,
                clock,
                Marketplace.WILDBERRIES,
                cooldownStore
        );
        return new WildberriesMarketplaceProvider(
                HttpClient.newHttpClient(),
                new WildberriesCardDetailUrlBuilder(stub.baseUri().resolve("/cards/v4/detail")),
                new WildberriesCardMapper(),
                coordinator,
                Duration.ofSeconds(2),
                Duration.ofMillis(1),
                Duration.ofSeconds(10),
                3,
                Duration.ofMinutes(5),
                100,
                Duration.ofHours(24),
                2 * 1024 * 1024,
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                Duration.ofSeconds(1),
                clock
        );
    }

    private MarketplaceProductRequest request() {
        return new MarketplaceProductRequest(
                Marketplace.WILDBERRIES,
                "123456789",
                MOSCOW
        );
    }

    private String fixture(String path) {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null)
                throw new IllegalArgumentException("Fixture not found: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class TestCooldownStore implements ProviderCooldownStore {

        private Instant cooldownUntil;
        private boolean failOnRead;
        private boolean failOnSave;

        @Override
        public Optional<Instant> findCooldownUntil(Marketplace marketplace) {
            if (failOnRead)
                throw new IllegalStateException("Database unavailable");
            return Optional.ofNullable(cooldownUntil);
        }

        @Override
        public void saveCooldownUntil(
                Marketplace marketplace,
                Instant cooldownUntil,
                Instant updatedAt
        ) {
            if (failOnSave)
                throw new IllegalStateException("Database unavailable");
            this.cooldownUntil = cooldownUntil;
        }
    }
}
