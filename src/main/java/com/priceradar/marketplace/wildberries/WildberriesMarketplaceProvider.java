package com.priceradar.marketplace.wildberries;

import com.priceradar.marketplace.application.MarketplaceCurrentProductRequest;
import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.application.MarketplaceProductRequest;
import com.priceradar.marketplace.application.MarketplaceProvider;
import com.priceradar.marketplace.application.MarketplaceProviderFailure;
import com.priceradar.marketplace.application.MarketplaceProviderFailureCode;
import com.priceradar.marketplace.application.MarketplaceProviderResult;
import com.priceradar.marketplace.application.ProviderAccessCoordinator;
import com.priceradar.marketplace.application.ProviderCooldownActiveException;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.product.application.VariantOption;
import com.priceradar.tracking.domain.WatchKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WildberriesMarketplaceProvider implements MarketplaceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(WildberriesMarketplaceProvider.class);
    private static final String USER_AGENT = "PriceRadar/0.1";
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofMinutes(15);
    private static final Duration ACCESS_FORBIDDEN_COOLDOWN = Duration.ofMinutes(30);
    private static final Duration SERVER_ERROR_COOLDOWN = Duration.ofMinutes(5);
    private static final Duration MAPPING_FAILURE_COOLDOWN = Duration.ofMinutes(15);
    private static final Duration MAX_BACKOFF_JITTER = Duration.ofSeconds(1);

    private final HttpClient httpClient;
    private final WildberriesCardDetailUrlBuilder urlBuilder;
    private final WildberriesCardMapper mapper;
    private final ProviderAccessCoordinator accessCoordinator;
    private final Duration requestTimeout;
    private final Duration baseBackoff;
    private final int maxAttempts;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Map<RequestCacheKey, CachedProduct> cache;

    public WildberriesMarketplaceProvider(
            HttpClient httpClient,
            WildberriesCardDetailUrlBuilder urlBuilder,
            WildberriesCardMapper mapper,
            ProviderAccessCoordinator accessCoordinator,
            Duration requestTimeout,
            Duration baseBackoff,
            int maxAttempts,
            Duration cacheTtl,
            int cacheMaxEntries,
            Clock clock
    ) {
        if (httpClient == null)
            throw new IllegalArgumentException("httpClient must not be null");
        if (urlBuilder == null)
            throw new IllegalArgumentException("urlBuilder must not be null");
        if (mapper == null)
            throw new IllegalArgumentException("mapper must not be null");
        if (accessCoordinator == null)
            throw new IllegalArgumentException("accessCoordinator must not be null");
        if (requestTimeout == null)
            throw new IllegalArgumentException("requestTimeout must not be null");
        if (requestTimeout.isNegative() || requestTimeout.isZero())
            throw new IllegalArgumentException("requestTimeout must be positive");
        if (baseBackoff == null)
            throw new IllegalArgumentException("baseBackoff must not be null");
        if (baseBackoff.isNegative() || baseBackoff.isZero())
            throw new IllegalArgumentException("baseBackoff must be positive");
        if (maxAttempts < 1)
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        if (cacheTtl == null)
            throw new IllegalArgumentException("cacheTtl must not be null");
        if (cacheTtl.isNegative() || cacheTtl.isZero())
            throw new IllegalArgumentException("cacheTtl must be positive");
        if (cacheMaxEntries < 1)
            throw new IllegalArgumentException("cacheMaxEntries must be at least 1");
        if (clock == null)
            throw new IllegalArgumentException("clock must not be null");

        this.httpClient = httpClient;
        this.urlBuilder = urlBuilder;
        this.mapper = mapper;
        this.accessCoordinator = accessCoordinator;
        this.requestTimeout = requestTimeout;
        this.baseBackoff = baseBackoff;
        this.maxAttempts = maxAttempts;
        this.cacheTtl = cacheTtl;
        this.clock = clock;
        this.cache = createCache(cacheMaxEntries);
    }

    @Override
    public Marketplace getMarketplace() {
        return Marketplace.WILDBERRIES;
    }

    @Override
    public MarketplaceProviderResult resolveProduct(MarketplaceProductRequest request) {
        String correlationId = newCorrelationId();

        if (request == null)
            return failure(
                    MarketplaceProviderFailureCode.INVALID_REQUEST,
                    "Marketplace request must not be null",
                    Optional.empty(),
                    correlationId
            );
        if (request.getMarketplace() != Marketplace.WILDBERRIES)
            return failure(
                    MarketplaceProviderFailureCode.INVALID_REQUEST,
                    "Wildberries provider cannot process another marketplace",
                    Optional.empty(),
                    correlationId
            );

        long nmId;
        try {
            nmId = parseNmId(request.getExternalProductId());
        } catch (IllegalArgumentException exception) {
            return failure(
                    MarketplaceProviderFailureCode.INVALID_REQUEST,
                    "Wildberries product id must be a positive number",
                    Optional.empty(),
                    correlationId
            );
        }

        RequestCacheKey cacheKey = new RequestCacheKey(
                nmId,
                request.getPriceContext().getDest(),
                request.getPriceContext().getSpp()
        );
        return fetchProduct(cacheKey, correlationId);
    }

    @Override
    public MarketplaceProviderResult fetchCurrent(MarketplaceCurrentProductRequest request) {
        String correlationId = newCorrelationId();

        if (request == null)
            return failure(
                    MarketplaceProviderFailureCode.INVALID_REQUEST,
                    "Current product request must not be null",
                    Optional.empty(),
                    correlationId
            );

        WatchKey watchKey = request.getWatchKey();
        if (watchKey.getMarketplace() != Marketplace.WILDBERRIES)
            return failure(
                    MarketplaceProviderFailureCode.INVALID_REQUEST,
                    "Wildberries provider cannot process another marketplace",
                    Optional.empty(),
                    correlationId
            );

        RequestCacheKey cacheKey = new RequestCacheKey(
                watchKey.getNmId(),
                watchKey.getDest(),
                watchKey.getSpp()
        );
        MarketplaceProviderResult result = fetchProduct(cacheKey, correlationId);

        if (!result.isSuccess())
            return result;

        MarketplaceProductDetails product = result.getProduct().orElseThrow();
        MarketplaceProductDetails fixedVariantProduct = selectFixedVariant(
                product,
                watchKey.getVariantKey()
        );

        return MarketplaceProviderResult.success(
                fixedVariantProduct,
                result.getObservedAt().orElseThrow()
        );
    }

    private MarketplaceProviderResult fetchProduct(RequestCacheKey cacheKey, String correlationId) {
        Optional<CachedProduct> cachedProduct = findCached(cacheKey);
        if (cachedProduct.isPresent())
            return MarketplaceProviderResult.success(
                    cachedProduct.get().getProduct(),
                    cachedProduct.get().getObservedAt()
            );

        URI uri;
        try {
            uri = urlBuilder.build(cacheKey.getNmId(), cacheKey.getDest(), cacheKey.getSpp());
        } catch (IllegalArgumentException exception) {
            return failure(
                    MarketplaceProviderFailureCode.INVALID_REQUEST,
                    "Failed to build Wildberries request URI",
                    Optional.empty(),
                    correlationId
            );
        }

        HttpRequest httpRequest = buildHttpRequest(uri);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean turnAcquired = false;
            Duration delayAfterRequest = Duration.ZERO;
            Optional<Instant> providerCooldownUntil = Optional.empty();

            try {
                accessCoordinator.awaitTurn();
                turnAcquired = true;

                cachedProduct = findCached(cacheKey);
                if (cachedProduct.isPresent())
                    return MarketplaceProviderResult.success(
                            cachedProduct.get().getProduct(),
                            cachedProduct.get().getObservedAt()
                    );

                HttpResponse<String> response = httpClient.send(
                        httpRequest,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    MarketplaceProviderResult mappedResult = mapSuccessfulResponse(
                            response.body(),
                            cacheKey.getNmId(),
                            correlationId
                    );

                    if (mappedResult.isSuccess()) {
                        MarketplaceProductDetails product = mappedResult.getProduct().orElseThrow();
                        Instant observedAt = mappedResult.getObservedAt().orElseThrow();
                        saveCached(cacheKey, product, observedAt);
                    } else if (isMappingFailure(mappedResult)) {
                        providerCooldownUntil = Optional.of(clock.instant().plus(MAPPING_FAILURE_COOLDOWN));
                        mappedResult = withRetryNotBefore(mappedResult, providerCooldownUntil.get());
                    }

                    return mappedResult;
                }

                if (statusCode == 404)
                    return failure(
                            MarketplaceProviderFailureCode.PRODUCT_NOT_FOUND,
                            "Wildberries product was not found",
                            Optional.empty(),
                            correlationId
                    );

                if (statusCode == 403) {
                    providerCooldownUntil = Optional.of(clock.instant().plus(ACCESS_FORBIDDEN_COOLDOWN));
                    return failure(
                            MarketplaceProviderFailureCode.ACCESS_FORBIDDEN,
                            "Wildberries temporarily rejected provider access",
                            providerCooldownUntil,
                            correlationId
                    );
                }

                if (statusCode == 429) {
                    Instant retryNotBefore = resolveRateLimitCooldown(response);
                    providerCooldownUntil = Optional.of(retryNotBefore);
                    return failure(
                            MarketplaceProviderFailureCode.RATE_LIMITED,
                            "Wildberries temporarily limited request frequency",
                            providerCooldownUntil,
                            correlationId
                    );
                }

                if (statusCode >= 500 && statusCode <= 599) {
                    Optional<Instant> retryAfter = findRetryAfter(response);
                    if (retryAfter.isPresent() && retryAfter.get().isAfter(clock.instant())) {
                        providerCooldownUntil = retryAfter;
                        return failure(
                                MarketplaceProviderFailureCode.SERVER_ERROR,
                                "Wildberries is temporarily unavailable",
                                providerCooldownUntil,
                                correlationId
                        );
                    }

                    if (attempt < maxAttempts) {
                        delayAfterRequest = calculateBackoff(attempt);
                        continue;
                    }

                    providerCooldownUntil = Optional.of(clock.instant().plus(SERVER_ERROR_COOLDOWN));
                    return failure(
                            MarketplaceProviderFailureCode.SERVER_ERROR,
                            "Wildberries is temporarily unavailable",
                            providerCooldownUntil,
                            correlationId
                    );
                }

                return failure(
                        MarketplaceProviderFailureCode.HTTP_ERROR,
                        "Wildberries returned unexpected HTTP status " + statusCode,
                        Optional.empty(),
                        correlationId
                );
            } catch (ProviderCooldownActiveException exception) {
                if (exception.isStateUnavailable()) {
                    LOGGER.warn(
                            "Provider safety state is unavailable; request blocked, correlationId={}, errorType={}",
                            correlationId,
                            exception.getCause().getClass().getSimpleName()
                    );
                }
                return failure(
                        MarketplaceProviderFailureCode.COOLDOWN_ACTIVE,
                        "Wildberries provider cooldown is active",
                        Optional.of(exception.getCooldownUntil()),
                        correlationId
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return failure(
                        MarketplaceProviderFailureCode.INTERRUPTED,
                        "Wildberries request was interrupted",
                        Optional.empty(),
                        correlationId
                );
            } catch (IOException exception) {
                if (attempt < maxAttempts) {
                    delayAfterRequest = calculateBackoff(attempt);
                    continue;
                }

                delayAfterRequest = calculateBackoff(attempt);
                return failure(
                        MarketplaceProviderFailureCode.TRANSPORT_ERROR,
                        "Wildberries request failed due to a network error",
                        Optional.of(clock.instant().plus(delayAfterRequest)),
                        correlationId
                );
            } finally {
                if (turnAcquired) {
                    if (providerCooldownUntil.isPresent()) {
                        boolean persisted = accessCoordinator.completeWithCooldown(providerCooldownUntil.get());
                        if (!persisted) {
                            LOGGER.warn(
                                    "Provider cooldown persistence failed; in-memory cooldown remains active, correlationId={}",
                                    correlationId
                            );
                        }
                    } else {
                        accessCoordinator.completeAfter(delayAfterRequest);
                    }
                }
            }
        }

        return failure(
                MarketplaceProviderFailureCode.TRANSPORT_ERROR,
                "Wildberries request could not be completed",
                Optional.empty(),
                correlationId
        );
    }

    private HttpRequest buildHttpRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build();
    }

    private MarketplaceProviderResult mapSuccessfulResponse(
            String responseBody,
            long expectedNmId,
            String correlationId
    ) {
        WildberriesMappingResult mappingResult = mapper.map(responseBody, expectedNmId);

        if (mappingResult.isSuccess()) {
            WildberriesMappedProduct mappedProduct = mappingResult.getProduct().orElseThrow();
            return MarketplaceProviderResult.success(
                    toProductDetails(mappedProduct),
                    clock.instant()
            );
        }

        WildberriesMappingFailure mappingFailure = mappingResult.getFailure().orElseThrow();
        MarketplaceProviderFailureCode failureCode = switch (mappingFailure.getCode()) {
            case PRODUCT_NOT_FOUND -> MarketplaceProviderFailureCode.PRODUCT_NOT_FOUND;
            case EMPTY_RESPONSE, MALFORMED_JSON -> MarketplaceProviderFailureCode.MALFORMED_RESPONSE;
            case SCHEMA_VIOLATION -> MarketplaceProviderFailureCode.SCHEMA_VIOLATION;
        };

        return failure(failureCode, mappingFailure.getMessage(), Optional.empty(), correlationId);
    }

    private MarketplaceProductDetails selectFixedVariant(
            MarketplaceProductDetails product,
            String variantKey
    ) {
        ProviderPriceFields priceFields = product.getPriceFieldsByVariantKey().get(variantKey);
        if (priceFields == null)
            priceFields = new ProviderPriceFields(false, Optional.empty(), Optional.empty());

        List<VariantOption> selectedOptions = product.getVariantOptions().stream()
                .filter(option -> option.getVariantKey().equals(variantKey))
                .toList();

        return new MarketplaceProductDetails(
                product.getMarketplace(),
                product.getExternalProductId(),
                product.getTitle(),
                product.getBrand(),
                selectedOptions,
                Map.of(variantKey, priceFields)
        );
    }

    private MarketplaceProductDetails toProductDetails(WildberriesMappedProduct mappedProduct) {
        return new MarketplaceProductDetails(
                Marketplace.WILDBERRIES,
                String.valueOf(mappedProduct.getNmId()),
                mappedProduct.getTitle(),
                mappedProduct.getBrand(),
                mappedProduct.getVariantOptions(),
                mappedProduct.getPriceFieldsByVariantKey()
        );
    }

    private boolean isMappingFailure(MarketplaceProviderResult result) {
        return result.getFailure()
                .map(MarketplaceProviderFailure::getCode)
                .filter(code -> code == MarketplaceProviderFailureCode.MALFORMED_RESPONSE
                        || code == MarketplaceProviderFailureCode.SCHEMA_VIOLATION)
                .isPresent();
    }

    private MarketplaceProviderResult withRetryNotBefore(
            MarketplaceProviderResult result,
            Instant retryNotBefore
    ) {
        MarketplaceProviderFailure previousFailure = result.getFailure().orElseThrow();
        return failure(
                previousFailure.getCode(),
                previousFailure.getMessage(),
                Optional.of(retryNotBefore),
                previousFailure.getCorrelationId()
        );
    }

    private Duration calculateBackoff(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 30);
        Duration exponentialBackoff = baseBackoff.multipliedBy(multiplier);
        long maxJitterMillis = MAX_BACKOFF_JITTER.toMillis();
        long jitterMillis = ThreadLocalRandom.current().nextLong(maxJitterMillis + 1);
        return exponentialBackoff.plusMillis(jitterMillis);
    }

    private Instant resolveRateLimitCooldown(HttpResponse<?> response) {
        Instant minimumCooldownUntil = clock.instant().plus(RATE_LIMIT_COOLDOWN);
        Optional<Instant> retryAfter = findRetryAfter(response);

        if (retryAfter.isPresent() && retryAfter.get().isAfter(minimumCooldownUntil))
            return retryAfter.get();
        return minimumCooldownUntil;
    }

    private Optional<Instant> findRetryAfter(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Retry-After")
                .flatMap(this::parseRetryAfter);
    }

    private Optional<Instant> parseRetryAfter(String rawValue) {
        String normalizedValue = rawValue.trim();

        try {
            long seconds = Long.parseLong(normalizedValue);
            if (seconds >= 0)
                return Optional.of(clock.instant().plusSeconds(seconds));
        } catch (NumberFormatException ignored) {
        }

        try {
            return Optional.of(ZonedDateTime.parse(
                    normalizedValue,
                    DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant());
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private long parseNmId(String externalProductId) {
        try {
            long nmId = Long.parseLong(externalProductId);
            if (nmId <= 0)
                throw new IllegalArgumentException("Wildberries nmId must be positive");
            return nmId;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Wildberries nmId must be numeric", exception);
        }
    }

    private synchronized Optional<CachedProduct> findCached(RequestCacheKey cacheKey) {
        CachedProduct cachedProduct = cache.get(cacheKey);
        if (cachedProduct == null)
            return Optional.empty();
        if (!clock.instant().isBefore(cachedProduct.getExpiresAt())) {
            cache.remove(cacheKey);
            return Optional.empty();
        }
        return Optional.of(cachedProduct);
    }

    private synchronized void saveCached(
            RequestCacheKey cacheKey,
            MarketplaceProductDetails product,
            Instant observedAt
    ) {
        cache.put(cacheKey, new CachedProduct(product, observedAt, observedAt.plus(cacheTtl)));
    }

    private Map<RequestCacheKey, CachedProduct> createCache(int cacheMaxEntries) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<RequestCacheKey, CachedProduct> eldest) {
                return size() > cacheMaxEntries;
            }
        };
    }

    private MarketplaceProviderResult failure(
            MarketplaceProviderFailureCode code,
            String message,
            Optional<Instant> retryNotBefore,
            String correlationId
    ) {
        return MarketplaceProviderResult.failure(new MarketplaceProviderFailure(
                code,
                message,
                retryNotBefore,
                correlationId
        ));
    }

    private String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    private static final class RequestCacheKey {

        private final long nmId;
        private final long dest;
        private final int spp;

        private RequestCacheKey(long nmId, long dest, int spp) {
            this.nmId = nmId;
            this.dest = dest;
            this.spp = spp;
        }

        private long getNmId() {
            return nmId;
        }

        private long getDest() {
            return dest;
        }

        private int getSpp() {
            return spp;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof RequestCacheKey that))
                return false;
            return nmId == that.nmId && dest == that.dest && spp == that.spp;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(nmId);
            result = 31 * result + Long.hashCode(dest);
            result = 31 * result + Integer.hashCode(spp);
            return result;
        }
    }

    private static final class CachedProduct {

        private final MarketplaceProductDetails product;
        private final Instant observedAt;
        private final Instant expiresAt;

        private CachedProduct(
                MarketplaceProductDetails product,
                Instant observedAt,
                Instant expiresAt
        ) {
            this.product = product;
            this.observedAt = observedAt;
            this.expiresAt = expiresAt;
        }

        private MarketplaceProductDetails getProduct() {
            return product;
        }

        private Instant getObservedAt() {
            return observedAt;
        }

        private Instant getExpiresAt() {
            return expiresAt;
        }
    }
}
