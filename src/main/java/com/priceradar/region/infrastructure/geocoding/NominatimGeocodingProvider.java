package com.priceradar.region.infrastructure.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.region.application.GeocodingProvider;
import com.priceradar.region.application.GeocodingResult;
import com.priceradar.region.domain.GeoCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NominatimGeocodingProvider implements GeocodingProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(NominatimGeocodingProvider.class);
    private static final List<String> SETTLEMENT_FIELDS = List.of(
            "city", "town", "village", "hamlet", "isolated_dwelling", "municipality"
    );
    private static final List<String> ALLOWED_PLACE_TYPES = List.of(
            "city", "town", "village", "settlement", "hamlet", "isolated_dwelling", "administrative"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final NominatimRequestCoordinator coordinator;
    private final URI baseUrl;
    private final Duration timeout;
    private final int maxResults;
    private final String userAgent;
    private final int maxResponseBytes;

    public NominatimGeocodingProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            NominatimRequestCoordinator coordinator,
            NominatimProperties properties
    ) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
        this.coordinator = java.util.Objects.requireNonNull(coordinator);
        this.baseUrl = requireHttps(properties.getBaseUrl());
        this.timeout = positive(properties.getTimeout(), "timeout");
        if (properties.getMaxResults() < 1 || properties.getMaxResults() > 40) {
            throw new IllegalArgumentException("Nominatim max results must be between 1 and 40");
        }
        if (properties.getUserAgent() == null || properties.getUserAgent().isBlank()) {
            throw new IllegalArgumentException("Nominatim identifying User-Agent is required");
        }
        if (properties.getMaxResponseBytes() < 1 || properties.getMaxResponseBytes() > 4 * 1024 * 1024) {
            throw new IllegalArgumentException("Nominatim response limit is invalid");
        }
        this.maxResults = properties.getMaxResults();
        this.userAgent = properties.getUserAgent().trim();
        this.maxResponseBytes = properties.getMaxResponseBytes();
    }

    @Override
    public GeocodingResult search(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("city query must not be blank");
        boolean acquired = false;
        try {
            coordinator.awaitTurn();
            acquired = true;
            HttpRequest request = HttpRequest.newBuilder(searchUri(query))
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                close(response.body());
                return GeocodingResult.temporarilyUnavailable();
            }
            return GeocodingResult.success(parse(readBounded(response.body())));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return GeocodingResult.temporarilyUnavailable();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Nominatim search failed, errorType={}", exception.getClass().getSimpleName());
            return GeocodingResult.temporarilyUnavailable();
        } finally {
            if (acquired) coordinator.complete();
        }
    }

    private List<GeoCandidate> parse(byte[] body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray()) throw new IOException("Nominatim response must be an array");
        List<GeoCandidate> candidates = new ArrayList<>();
        for (JsonNode result : root) {
            toCandidate(result).ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    private Optional<GeoCandidate> toCandidate(JsonNode result) {
        JsonNode address = result.path("address");
        String category = text(result, "category");
        String placeType = text(result, "type");
        if (!address.isObject() || !ALLOWED_PLACE_TYPES.contains(placeType)) return Optional.empty();
        boolean place = "place".equals(category);
        boolean administrativeBoundary = "boundary".equals(category) && "administrative".equals(placeType);
        if (!place && !administrativeBoundary) return Optional.empty();
        if (address.hasNonNull("road") || address.hasNonNull("house_number")) return Optional.empty();

        String settlement = firstText(address, SETTLEMENT_FIELDS);
        if (settlement.isBlank()) return Optional.empty();
        String countryCode = text(address, "country_code");
        if (!"ru".equalsIgnoreCase(countryCode)) return Optional.empty();
        try {
            return Optional.of(new GeoCandidate(
                    settlement,
                    text(address, "state"),
                    firstText(address, List.of("county", "state_district", "municipality")),
                    text(address, "country"),
                    new BigDecimal(text(result, "lat")),
                    new BigDecimal(text(result, "lon")),
                    text(result, "osm_type"),
                    text(result, "osm_id"),
                    category,
                    placeType,
                    result.path("importance").asDouble(0),
                    result.path("place_rank").asInt(0)
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private URI searchUri(String query) {
        String separator = baseUrl.toString().contains("?") ? "&" : "?";
        return URI.create(baseUrl + separator
                + "q=" + encode(query.trim())
                + "&format=jsonv2&addressdetails=1&featureType=settlement"
                + "&countrycodes=ru&accept-language=ru&limit="
                + maxResults);
    }

    private byte[] readBounded(InputStream input) throws IOException {
        try (input) {
            byte[] body = input.readNBytes(maxResponseBytes + 1);
            if (body.length > maxResponseBytes) throw new IOException("Nominatim response is too large");
            return body;
        }
    }

    private void close(InputStream input) {
        try (input) { }
        catch (IOException ignored) { }
    }

    private String firstText(JsonNode node, List<String> fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() || value.isNumber() ? value.asText().trim() : "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private URI requireHttps(URI value) {
        if (value == null || value.getHost() == null
                || !("https".equalsIgnoreCase(value.getScheme()) || isLoopback(value))) {
            throw new IllegalArgumentException("Nominatim base URL must use HTTPS");
        }
        return value;
    }

    private boolean isLoopback(URI value) {
        return "http".equalsIgnoreCase(value.getScheme())
                && ("localhost".equalsIgnoreCase(value.getHost()) || "127.0.0.1".equals(value.getHost()));
    }

    private Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
