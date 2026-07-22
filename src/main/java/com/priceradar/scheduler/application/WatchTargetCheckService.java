package com.priceradar.scheduler.application;

import com.priceradar.marketplace.application.MarketplaceCurrentProductRequest;
import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.application.MarketplaceProvider;
import com.priceradar.marketplace.application.MarketplaceProviderFailure;
import com.priceradar.marketplace.application.MarketplaceProviderFailureCode;
import com.priceradar.marketplace.application.MarketplaceProviderResult;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.pricing.application.ProviderPriceFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WatchTargetCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchTargetCheckService.class);

    private final Map<Marketplace, MarketplaceProvider> providers;
    private final PriceSemanticsService priceSemanticsService;
    private final WatchTargetCheckTransaction checkTransaction;
    private final ScheduleJitter scheduleJitter;
    private final Clock clock;
    private final Duration refreshInterval;
    private final Duration failureRetryDelay;

    public WatchTargetCheckService(
            List<MarketplaceProvider> providers,
            PriceSemanticsService priceSemanticsService,
            WatchTargetCheckTransaction checkTransaction,
            ScheduleJitter scheduleJitter,
            Clock clock,
            Duration refreshInterval,
            Duration failureRetryDelay
    ) {
        if (providers == null || priceSemanticsService == null || checkTransaction == null
                || scheduleJitter == null || clock == null || refreshInterval == null
                || failureRetryDelay == null) {
            throw new IllegalArgumentException("watch target check dependencies must not be null");
        }
        validatePositive(refreshInterval, "refreshInterval");
        validatePositive(failureRetryDelay, "failureRetryDelay");
        this.providers = indexProviders(providers);
        this.priceSemanticsService = priceSemanticsService;
        this.checkTransaction = checkTransaction;
        this.scheduleJitter = scheduleJitter;
        this.clock = clock;
        this.refreshInterval = refreshInterval;
        this.failureRetryDelay = failureRetryDelay;
    }

    public WatchTargetCheckOutcome check(DueWatchTarget target, UUID checkId) {
        if (target == null || checkId == null) {
            throw new IllegalArgumentException("watch target check fields must not be null");
        }
        MarketplaceProvider provider = providerFor(target.getWatchKey().getMarketplace());
        MarketplaceProviderResult result = provider.fetchCurrent(
                new MarketplaceCurrentProductRequest(target.getWatchKey())
        );
        Instant completedAt = clock.instant();

        if (!result.isSuccess()) {
            return handleFailure(target, result.getFailure().orElseThrow(), completedAt);
        }

        MarketplaceProductDetails product = result.getProduct().orElseThrow();
        Instant observedAt = result.getObservedAt().orElseThrow();
        InterpretedPrice price;
        try {
            ProviderPriceFields fields = validateAndFindPriceFields(target, product);
            price = priceSemanticsService.interpret(fields);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Instant nextCheckAt = completedAt.plus(failureRetryDelay);
            checkTransaction.persistFailure(target, completedAt, nextCheckAt);
            LOGGER.error(
                    "Scheduled provider observation was rejected, watchTargetId={}, provider={}, errorType={}, nextCheckAt={}",
                    target.getWatchTargetId(),
                    target.getWatchKey().getMarketplace(),
                    exception.getClass().getSimpleName(),
                    nextCheckAt
            );
            return WatchTargetCheckOutcome.PROVIDER_FAILURE;
        }
        Instant nextCheckAt = completedAt
                .plus(refreshInterval)
                .plus(scheduleJitter.next());
        checkTransaction.persistObservation(
                target,
                checkId,
                product,
                price,
                observedAt,
                completedAt,
                nextCheckAt
        );
        return WatchTargetCheckOutcome.OBSERVATION_SAVED;
    }

    private WatchTargetCheckOutcome handleFailure(
            DueWatchTarget target,
            MarketplaceProviderFailure failure,
            Instant completedAt
    ) {
        Instant fallbackRetry = completedAt.plus(failureRetryDelay);
        Instant nextCheckAt = failure.getRetryNotBefore()
                .filter(retryAt -> retryAt.isAfter(completedAt))
                .orElse(fallbackRetry);
        checkTransaction.persistFailure(target, completedAt, nextCheckAt);
        LOGGER.warn(
                "Scheduled provider check failed, watchTargetId={}, provider={}, errorCode={}, correlationId={}, nextCheckAt={}",
                target.getWatchTargetId(),
                target.getWatchKey().getMarketplace(),
                failure.getCode(),
                failure.getCorrelationId(),
                nextCheckAt
        );
        return activatesCooldown(failure.getCode())
                ? WatchTargetCheckOutcome.PROVIDER_COOLDOWN
                : WatchTargetCheckOutcome.PROVIDER_FAILURE;
    }

    private ProviderPriceFields validateAndFindPriceFields(
            DueWatchTarget target,
            MarketplaceProductDetails product
    ) {
        if (product.getMarketplace() != target.getWatchKey().getMarketplace()
                || !product.getExternalProductId().equals(
                        String.valueOf(target.getWatchKey().getNmId())
                )) {
            throw new IllegalStateException("Provider returned another product for watch target");
        }
        ProviderPriceFields fields = product.getPriceFieldsByVariantKey().get(
                target.getWatchKey().getVariantKey()
        );
        if (fields == null) {
            throw new IllegalStateException("Provider omitted the fixed watch target variant");
        }
        return fields;
    }

    private MarketplaceProvider providerFor(Marketplace marketplace) {
        MarketplaceProvider provider = providers.get(marketplace);
        if (provider == null) {
            throw new IllegalStateException("No provider configured for " + marketplace);
        }
        return provider;
    }

    private Map<Marketplace, MarketplaceProvider> indexProviders(
            List<MarketplaceProvider> providerList
    ) {
        Map<Marketplace, MarketplaceProvider> indexed = new EnumMap<>(Marketplace.class);
        for (MarketplaceProvider provider : providerList) {
            if (provider == null) {
                throw new IllegalArgumentException("providers must not contain null elements");
            }
            if (indexed.put(provider.getMarketplace(), provider) != null) {
                throw new IllegalArgumentException(
                        "only one provider can be registered per marketplace"
                );
            }
        }
        if (indexed.isEmpty()) {
            throw new IllegalArgumentException("at least one marketplace provider is required");
        }
        return Map.copyOf(indexed);
    }

    private boolean activatesCooldown(MarketplaceProviderFailureCode code) {
        return code == MarketplaceProviderFailureCode.COOLDOWN_ACTIVE
                || code == MarketplaceProviderFailureCode.ACCESS_FORBIDDEN
                || code == MarketplaceProviderFailureCode.RATE_LIMITED
                || code == MarketplaceProviderFailureCode.SERVER_ERROR
                || code == MarketplaceProviderFailureCode.MALFORMED_RESPONSE
                || code == MarketplaceProviderFailureCode.SCHEMA_VIOLATION;
    }

    private void validatePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
