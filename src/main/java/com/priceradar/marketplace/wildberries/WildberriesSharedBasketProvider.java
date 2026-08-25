package com.priceradar.marketplace.wildberries;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.application.ProviderAccessCoordinator;
import com.priceradar.marketplace.application.ProviderCooldownActiveException;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.product.application.VariantOption;
import com.priceradar.sharedbasket.application.ResolvedSharedBasketItem;
import com.priceradar.sharedbasket.application.SharedBasketFailure;
import com.priceradar.sharedbasket.application.SharedBasketFailureCode;
import com.priceradar.sharedbasket.application.SharedBasketItem;
import com.priceradar.sharedbasket.application.SharedBasketProductResolution;
import com.priceradar.sharedbasket.application.SharedBasketProductResolver;
import com.priceradar.sharedbasket.application.SharedBasketProvider;
import com.priceradar.sharedbasket.application.SharedBasketProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class WildberriesSharedBasketProvider
        implements SharedBasketProvider, SharedBasketProductResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(WildberriesSharedBasketProvider.class);
    private static final String USER_AGENT = "PriceRadar/0.1";
    private static final int BATCH_SIZE = 50;
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofMinutes(15);
    private static final Duration SERVER_ERROR_COOLDOWN = Duration.ofMinutes(5);
    private static final Duration INVALID_RESPONSE_COOLDOWN = Duration.ofMinutes(15);

    private final HttpClient httpClient;
    private final URI basketEndpoint;
    private final URI cardsEndpoint;
    private final WildberriesSharedBasketMapper basketMapper;
    private final WildberriesCardMapper cardMapper;
    private final ProviderAccessCoordinator accessCoordinator;
    private final Duration requestTimeout;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final Duration maxRetryAfter;
    private final int maxAttempts;
    private final int maxResponseBytes;
    private final Clock clock;

    public WildberriesSharedBasketProvider(
            HttpClient httpClient,
            URI basketEndpoint,
            URI cardsEndpoint,
            WildberriesSharedBasketMapper basketMapper,
            WildberriesCardMapper cardMapper,
            ProviderAccessCoordinator accessCoordinator,
            Duration requestTimeout,
            Duration baseBackoff,
            Duration maxBackoff,
            Duration maxRetryAfter,
            int maxAttempts,
            int maxResponseBytes,
            Clock clock
    ) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.basketEndpoint = validateEndpoint(basketEndpoint, "wbx-api-gateway.wildberries.ru", true);
        this.cardsEndpoint = validateEndpoint(cardsEndpoint, "card.wb.ru", false);
        this.basketMapper = java.util.Objects.requireNonNull(basketMapper, "basketMapper must not be null");
        this.cardMapper = java.util.Objects.requireNonNull(cardMapper, "cardMapper must not be null");
        this.accessCoordinator = java.util.Objects.requireNonNull(accessCoordinator, "accessCoordinator must not be null");
        this.requestTimeout = positive(requestTimeout, "requestTimeout");
        this.baseBackoff = positive(baseBackoff, "baseBackoff");
        this.maxBackoff = positive(maxBackoff, "maxBackoff");
        this.maxRetryAfter = positive(maxRetryAfter, "maxRetryAfter");
        if (baseBackoff.compareTo(maxBackoff) > 0) throw new IllegalArgumentException("baseBackoff must not exceed maxBackoff");
        if (maxAttempts < 1 || maxAttempts > 10) throw new IllegalArgumentException("maxAttempts must be between 1 and 10");
        if (maxResponseBytes < 1 || maxResponseBytes > 16 * 1024 * 1024) throw new IllegalArgumentException("invalid maxResponseBytes");
        this.maxAttempts = maxAttempts;
        this.maxResponseBytes = maxResponseBytes;
        this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public SharedBasketProviderResult fetch(String shareId) {
        if (shareId == null || !shareId.matches("[a-z0-9]{10}")) {
            throw new IllegalArgumentException("shareId must contain 10 lowercase letters or digits");
        }
        HttpOutcome outcome = get(URI.create(basketEndpoint.toString() + shareId));
        if (!outcome.isSuccess()) return SharedBasketProviderResult.failure(outcome.getFailure().orElseThrow());
        SharedBasketProviderResult result = basketMapper.map(outcome.getBody().orElseThrow());
        if (!result.isSuccess() && isInvalidResponse(result.getFailure().orElseThrow().getCode())) {
            Optional<SharedBasketFailure> cooldownFailure = activateInvalidResponseCooldown();
            if (cooldownFailure.isPresent()) return SharedBasketProviderResult.failure(cooldownFailure.orElseThrow());
        }
        return result;
    }

    @Override
    public SharedBasketProductResolution resolveExact(
            List<SharedBasketItem> items,
            PriceContext priceContext
    ) {
        if (items == null || priceContext == null || items.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("basket items and price context must not be null");
        }
        List<SharedBasketItem> uniqueItems = new ArrayList<>(new LinkedHashSet<>(items));
        List<ResolvedSharedBasketItem> resolved = new ArrayList<>();
        int skipped = 0;

        for (int start = 0; start < uniqueItems.size(); start += BATCH_SIZE) {
            List<SharedBasketItem> chunk = uniqueItems.subList(start, Math.min(start + BATCH_SIZE, uniqueItems.size()));
            List<Long> nmIds = chunk.stream().map(SharedBasketItem::getNmId).distinct().toList();
            HttpOutcome outcome = get(buildCardsUri(nmIds, priceContext));
            if (!outcome.isSuccess()) return SharedBasketProductResolution.failure(outcome.getFailure().orElseThrow());

            WildberriesBatchMappingResult mapping = cardMapper.mapBatch(outcome.getBody().orElseThrow(), nmIds);
            if (!mapping.isSuccess()) {
                WildberriesMappingFailure failure = mapping.getFailure().orElseThrow();
                Optional<SharedBasketFailure> cooldownFailure = activateInvalidResponseCooldown();
                if (cooldownFailure.isPresent()) {
                    return SharedBasketProductResolution.failure(cooldownFailure.orElseThrow());
                }
                return SharedBasketProductResolution.failure(new SharedBasketFailure(
                        sharedFailureCode(failure.getCode()),
                        failure.getMessage()
                ));
            }
            Optional<WildberriesMappingFailure> invalidProduct = mapping.getProducts().values().stream()
                    .filter(result -> !result.isSuccess())
                    .map(result -> result.getFailure().orElseThrow())
                    .filter(failure -> failure.getCode() == WildberriesMappingFailureCode.MALFORMED_JSON
                            || failure.getCode() == WildberriesMappingFailureCode.SCHEMA_VIOLATION)
                    .findFirst();
            if (invalidProduct.isPresent()) {
                Optional<SharedBasketFailure> cooldownFailure = activateInvalidResponseCooldown();
                if (cooldownFailure.isPresent()) {
                    return SharedBasketProductResolution.failure(cooldownFailure.orElseThrow());
                }
                return SharedBasketProductResolution.failure(new SharedBasketFailure(
                        SharedBasketFailureCode.SCHEMA_VIOLATION,
                        invalidProduct.orElseThrow().getMessage()
                ));
            }
            Instant observedAt = clock.instant();
            for (SharedBasketItem item : chunk) {
                Optional<ResolvedSharedBasketItem> exact = resolveExact(
                        item,
                        mapping.getProducts().get(item.getNmId()),
                        observedAt
                );
                if (exact.isPresent()) resolved.add(exact.orElseThrow());
                else skipped++;
            }
        }
        return SharedBasketProductResolution.success(resolved, skipped);
    }

    private Optional<ResolvedSharedBasketItem> resolveExact(
            SharedBasketItem item,
            WildberriesMappingResult mapping,
            Instant observedAt
    ) {
        if (mapping == null || !mapping.isSuccess()) return Optional.empty();
        WildberriesMappedProduct mapped = mapping.getProduct().orElseThrow();
        String expectedKey = ResolvedVariant.wildberriesSize(
                item.getChrtId(),
                List.of(),
                false
        ).getVariantKey();
        Optional<VariantOption> option = mapped.getVariantOptions().stream()
                .filter(candidate -> candidate.getVariantKey().equals(expectedKey))
                .findFirst();
        ProviderPriceFields fields = mapped.getPriceFieldsByVariantKey().get(expectedKey);
        if (option.isEmpty() || fields == null) return Optional.empty();

        MarketplaceProductDetails product = new MarketplaceProductDetails(
                Marketplace.WILDBERRIES,
                String.valueOf(mapped.getNmId()),
                mapped.getTitle(),
                mapped.getBrand(),
                List.of(option.orElseThrow()),
                Map.of(expectedKey, fields)
        );
        return Optional.of(new ResolvedSharedBasketItem(
                item,
                product,
                ResolvedVariant.fromOption(option.orElseThrow(), false),
                fields,
                observedAt
        ));
    }

    private URI buildCardsUri(List<Long> nmIds, PriceContext context) {
        String joined = String.join(";", nmIds.stream().map(String::valueOf).toList());
        return URI.create(cardsEndpoint + "?appType=1&curr=rub&dest=" + context.getDest()
                + "&spp=" + context.getSpp() + "&nm=" + joined);
    }

    private HttpOutcome get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET().timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean acquired = false;
            Duration completionDelay = Duration.ZERO;
            Optional<Instant> cooldown = Optional.empty();
            try {
                accessCoordinator.awaitTurn();
                acquired = true;
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status == 200) return HttpOutcome.success(readBody(response.body()));
                response.body().close();
                if (status == 404) return failure(SharedBasketFailureCode.NOT_FOUND, "Shared basket or product data was not found");
                if (status == 429) {
                    cooldown = Optional.of(resolveCooldown(response, RATE_LIMIT_COOLDOWN));
                    return failure(SharedBasketFailureCode.RATE_LIMITED, "Wildberries rate limit is active");
                }
                if (status == 403) {
                    cooldown = Optional.of(clock.instant().plus(RATE_LIMIT_COOLDOWN));
                    return failure(SharedBasketFailureCode.TEMPORARILY_UNAVAILABLE, "Wildberries rejected the request");
                }
                if (status >= 500 && status <= 599) {
                    Optional<Instant> retryAfter = retryAfter(response);
                    if (retryAfter.isPresent() && retryAfter.orElseThrow().isAfter(clock.instant())) {
                        cooldown = retryAfter;
                        return failure(SharedBasketFailureCode.TEMPORARILY_UNAVAILABLE, "Wildberries is temporarily unavailable");
                    }
                    if (attempt < maxAttempts) {
                        completionDelay = backoff(attempt);
                        continue;
                    }
                    cooldown = Optional.of(clock.instant().plus(SERVER_ERROR_COOLDOWN));
                    return failure(SharedBasketFailureCode.TEMPORARILY_UNAVAILABLE, "Wildberries is temporarily unavailable");
                }
                return failure(SharedBasketFailureCode.TEMPORARILY_UNAVAILABLE, "Wildberries returned HTTP " + status);
            } catch (ProviderCooldownActiveException exception) {
                return failure(SharedBasketFailureCode.COOLDOWN_ACTIVE, "Wildberries provider cooldown is active");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return failure(SharedBasketFailureCode.INTERRUPTED, "Wildberries request was interrupted");
            } catch (IOException exception) {
                if (attempt < maxAttempts) {
                    completionDelay = backoff(attempt);
                    continue;
                }
                return failure(SharedBasketFailureCode.TRANSPORT_ERROR, "Wildberries request failed");
            } finally {
                if (acquired) {
                    if (cooldown.isPresent()) {
                        if (!accessCoordinator.completeWithCooldown(cooldown.orElseThrow())) {
                            LOGGER.warn("Could not persist Wildberries cooldown after shared basket request");
                        }
                    } else {
                        accessCoordinator.completeAfter(completionDelay);
                    }
                }
            }
        }
        return failure(SharedBasketFailureCode.TRANSPORT_ERROR, "Wildberries request failed");
    }

    private String readBody(InputStream input) throws IOException {
        try (input) {
            byte[] bytes = input.readNBytes(maxResponseBytes + 1);
            if (bytes.length > maxResponseBytes) throw new IOException("response is too large");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private Instant resolveCooldown(HttpResponse<?> response, Duration minimum) {
        Instant minimumUntil = clock.instant().plus(minimum);
        return retryAfter(response).filter(value -> value.isAfter(minimumUntil)).orElse(minimumUntil);
    }

    private Optional<Instant> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            Instant now = clock.instant();
            Instant maximum = now.plus(maxRetryAfter);
            try {
                long seconds = Long.parseLong(value.trim());
                if (seconds >= 0) return Optional.of(seconds > maxRetryAfter.toSeconds() ? maximum : now.plusSeconds(seconds));
            } catch (NumberFormatException | ArithmeticException ignored) {
            }
            try {
                Instant parsed = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Optional.of(parsed.isAfter(maximum) ? maximum : parsed);
            } catch (DateTimeParseException ignored) {
            }
            return Optional.empty();
        });
    }

    private Duration backoff(int attempt) {
        try {
            Duration result = baseBackoff.multipliedBy(1L << Math.min(attempt - 1, 30));
            return result.compareTo(maxBackoff) > 0 ? maxBackoff : result;
        } catch (ArithmeticException exception) {
            return maxBackoff;
        }
    }

    private HttpOutcome failure(SharedBasketFailureCode code, String message) {
        return HttpOutcome.failure(new SharedBasketFailure(code, message));
    }

    private boolean isInvalidResponse(SharedBasketFailureCode code) {
        return code == SharedBasketFailureCode.MALFORMED_RESPONSE
                || code == SharedBasketFailureCode.SCHEMA_VIOLATION;
    }

    private SharedBasketFailureCode sharedFailureCode(WildberriesMappingFailureCode code) {
        return switch (code) {
            case EMPTY_RESPONSE, MALFORMED_JSON -> SharedBasketFailureCode.MALFORMED_RESPONSE;
            case SCHEMA_VIOLATION -> SharedBasketFailureCode.SCHEMA_VIOLATION;
            case PRODUCT_NOT_FOUND -> SharedBasketFailureCode.NOT_FOUND;
        };
    }

    private Optional<SharedBasketFailure> activateInvalidResponseCooldown() {
        try {
            if (!accessCoordinator.activateCooldown(clock.instant().plus(INVALID_RESPONSE_COOLDOWN))) {
                LOGGER.warn("Could not persist Wildberries cooldown after invalid shared basket response");
            }
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.of(new SharedBasketFailure(
                    SharedBasketFailureCode.INTERRUPTED,
                    "Wildberries response processing was interrupted"
            ));
        }
    }

    private static URI validateEndpoint(URI endpoint, String productionHost, boolean requireTrailingSlash) {
        if (endpoint == null || !endpoint.isAbsolute() || endpoint.getHost() == null
                || endpoint.getQuery() != null || endpoint.getFragment() != null || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("Wildberries endpoint is invalid");
        }
        boolean loopback = Set.of("localhost", "127.0.0.1", "::1", "[::1]")
                .contains(endpoint.getHost().toLowerCase());
        if (!("https".equalsIgnoreCase(endpoint.getScheme()) && productionHost.equalsIgnoreCase(endpoint.getHost()))
                && !("http".equalsIgnoreCase(endpoint.getScheme()) && loopback)) {
            throw new IllegalArgumentException("Wildberries endpoint host is not allowed");
        }
        if (requireTrailingSlash && !endpoint.getPath().endsWith("/")) {
            throw new IllegalArgumentException("shared basket endpoint must end with slash");
        }
        return endpoint;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static final class HttpOutcome {
        private final Optional<String> body;
        private final Optional<SharedBasketFailure> failure;
        private HttpOutcome(Optional<String> body, Optional<SharedBasketFailure> failure) {
            this.body = body; this.failure = failure;
        }
        static HttpOutcome success(String body) { return new HttpOutcome(Optional.of(body), Optional.empty()); }
        static HttpOutcome failure(SharedBasketFailure failure) { return new HttpOutcome(Optional.empty(), Optional.of(failure)); }
        boolean isSuccess() { return body.isPresent(); }
        Optional<String> getBody() { return body; }
        Optional<SharedBasketFailure> getFailure() { return failure; }
    }
}
