package com.priceradar.scheduler.application;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.application.MarketplaceProvider;
import com.priceradar.marketplace.application.MarketplaceProviderFailure;
import com.priceradar.marketplace.application.MarketplaceProviderFailureCode;
import com.priceradar.marketplace.application.MarketplaceProviderResult;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.tracking.domain.WatchKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchTargetCheckServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T07:00:00Z");
    private static final Duration JITTER = Duration.ofMinutes(10);

    @Mock
    private MarketplaceProvider provider;

    @Mock
    private WatchTargetCheckTransaction checkTransaction;

    private WatchTargetCheckService checkService;

    @BeforeEach
    void setUp() {
        when(provider.getMarketplace()).thenReturn(Marketplace.WILDBERRIES);
        checkService = new WatchTargetCheckService(
                List.of(provider),
                new PriceSemanticsService(),
                checkTransaction,
                () -> JITTER,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(6),
                Duration.ofMinutes(5)
        );
    }

    @Test
    void persistsOneSharedObservationAndSchedulesSixHoursWithJitter() {
        DueWatchTarget target = dueTarget();
        UUID checkId = UUID.randomUUID();
        MarketplaceProductDetails product = productDetails();
        when(provider.fetchCurrent(any())).thenReturn(
                MarketplaceProviderResult.success(product, NOW.minusSeconds(1))
        );

        WatchTargetCheckOutcome outcome = checkService.check(target, checkId);

        assertThat(outcome).isEqualTo(WatchTargetCheckOutcome.OBSERVATION_SAVED);
        verify(provider).fetchCurrent(any());
        verify(checkTransaction).persistObservation(
                target,
                checkId,
                product,
                new PriceSemanticsService().interpret(
                        product.getPriceFieldsByVariantKey().get("SIZE:77")
                ),
                NOW.minusSeconds(1),
                NOW,
                NOW.plus(Duration.ofHours(6)).plus(JITTER)
        );
    }

    @Test
    void honorsProviderCooldownAndStopsNormalProcessing() {
        DueWatchTarget target = dueTarget();
        Instant retryNotBefore = NOW.plus(Duration.ofMinutes(15));
        MarketplaceProviderFailure failure = new MarketplaceProviderFailure(
                MarketplaceProviderFailureCode.RATE_LIMITED,
                "rate limited",
                Optional.of(retryNotBefore),
                "test-correlation"
        );
        when(provider.fetchCurrent(any())).thenReturn(MarketplaceProviderResult.failure(failure));

        WatchTargetCheckOutcome outcome = checkService.check(target, UUID.randomUUID());

        assertThat(outcome).isEqualTo(WatchTargetCheckOutcome.PROVIDER_COOLDOWN);
        verify(checkTransaction).persistFailure(target, NOW, retryNotBefore);
    }

    private DueWatchTarget dueTarget() {
        WatchKey watchKey = WatchKey.forSize(
                Marketplace.WILDBERRIES,
                123456L,
                77L,
                1259570991L,
                30
        );
        return new DueWatchTarget(
                UUID.randomUUID(),
                UUID.randomUUID(),
                watchKey,
                NOW.minusSeconds(1)
        );
    }

    private MarketplaceProductDetails productDetails() {
        ProviderPriceFields priceFields = new ProviderPriceFields(
                true,
                Optional.of(RubleAmount.ofMinorUnits(10_000L)),
                Optional.of(RubleAmount.ofMinorUnits(12_000L))
        );
        return new MarketplaceProductDetails(
                Marketplace.WILDBERRIES,
                "123456",
                Optional.of("Кофемолка"),
                Optional.of("PriceRadar Test"),
                List.of(),
                Map.of("SIZE:77", priceFields)
        );
    }
}
