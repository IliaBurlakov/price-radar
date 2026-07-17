package com.priceradar.persistence;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.application.ProviderCooldownStore;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.product.application.ResolvedQuotePersistenceCommand;
import com.priceradar.product.application.ResolvedQuotePersistenceService;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.product.infrastructure.persistence.ProductJpaRepository;
import com.priceradar.tracking.infrastructure.persistence.PriceSnapshotJpaRepository;
import com.priceradar.tracking.infrastructure.persistence.WatchTargetJpaRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PersistenceSmokeTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private ProviderCooldownStore cooldownStore;

    @Autowired
    private ResolvedQuotePersistenceService quotePersistenceService;

    @Autowired
    private ResolvedQuoteService resolvedQuoteService;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private WatchTargetJpaRepository watchTargetRepository;

    @Autowired
    private PriceSnapshotJpaRepository snapshotRepository;

    @Test
    void startsContextWithFlywayAndPersistsProviderCooldown() {
        Instant updatedAt = Instant.parse("2026-07-12T10:00:00Z");
        Instant cooldownUntil = Instant.parse("2026-07-12T10:15:00Z");

        cooldownStore.saveCooldownUntil(
                Marketplace.WILDBERRIES,
                cooldownUntil,
                updatedAt
        );

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(cooldownStore.findCooldownUntil(Marketplace.WILDBERRIES))
                .contains(cooldownUntil);
    }

    @Test
    void persistsResolvedQuoteIdempotentlyAndChecksTtlFromSnapshots() {
        Instant freshObservation = Instant.now().minus(1, ChronoUnit.MINUTES);
        ResolvedQuotePersistenceCommand freshQuote = quoteCommand(123456L, freshObservation);

        UUID firstTargetId = quotePersistenceService.save(freshQuote);
        UUID repeatedTargetId = quotePersistenceService.save(freshQuote);

        assertThat(repeatedTargetId).isEqualTo(firstTargetId);
        assertThat(resolvedQuoteService.isFresh(firstTargetId)).isTrue();
        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(watchTargetRepository.count()).isEqualTo(1);
        assertThat(snapshotRepository.count()).isEqualTo(1);

        Instant expiredObservation = Instant.now().minus(16, ChronoUnit.MINUTES);
        ResolvedQuotePersistenceCommand expiredQuote = quoteCommand(654321L, expiredObservation);
        UUID expiredTargetId = quotePersistenceService.save(expiredQuote);

        assertThat(resolvedQuoteService.isFresh(expiredTargetId)).isFalse();
        assertThat(productRepository.count()).isEqualTo(2);
        assertThat(watchTargetRepository.count()).isEqualTo(2);
        assertThat(snapshotRepository.count()).isEqualTo(2);
    }

    private ResolvedQuotePersistenceCommand quoteCommand(long nmId, Instant observedAt) {
        PriceContext priceContext = new PriceContext("Moscow", 1259570991L, 30);
        ProviderPriceFields priceFields = new ProviderPriceFields(
                true,
                Optional.of(RubleAmount.ofMinorUnits(10_000L)),
                Optional.of(RubleAmount.ofMinorUnits(12_000L))
        );
        InterpretedPrice interpretedPrice = new PriceSemanticsService().interpret(priceFields);
        String variantKey = ResolvedVariant.noVariant().getVariantKey();
        MarketplaceProductDetails product = new MarketplaceProductDetails(
                Marketplace.WILDBERRIES,
                String.valueOf(nmId),
                Optional.of("Test product"),
                Optional.of("Test brand"),
                List.of(),
                Map.of(variantKey, priceFields)
        );

        return new ResolvedQuotePersistenceCommand(
                product,
                nmId,
                "https://www.wildberries.ru/catalog/%d/detail.aspx".formatted(nmId),
                ResolvedVariant.noVariant(),
                priceContext,
                interpretedPrice,
                observedAt
        );
    }
}
