package com.priceradar.marketplace.wildberries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.marketplace.application.ProviderAccessCoordinator;
import com.priceradar.marketplace.application.ProviderCooldownActiveException;
import com.priceradar.region.application.WildberriesGeoProvider;
import com.priceradar.region.application.WildberriesGeoResult;
import com.priceradar.region.application.GeoLocationLabelFormatter;
import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.WildberriesLocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WildberriesGeoHttpProvider implements WildberriesGeoProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(WildberriesGeoHttpProvider.class);
    private static final String USER_AGENT = "PriceRadar/0.1";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ProviderAccessCoordinator accessCoordinator;
    private final URI endpoint;
    private final Duration timeout;
    private final int maxResponseBytes;
    private final Clock clock;
    private final GeoLocationLabelFormatter locationLabelFormatter;

    public WildberriesGeoHttpProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ProviderAccessCoordinator accessCoordinator,
            URI endpoint,
            Duration timeout,
            int maxResponseBytes,
            Clock clock,
            GeoLocationLabelFormatter locationLabelFormatter
    ) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
        this.accessCoordinator = java.util.Objects.requireNonNull(accessCoordinator);
        this.endpoint = validateEndpoint(endpoint);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("WB geo timeout must be positive");
        }
        if (maxResponseBytes < 1 || maxResponseBytes > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("WB geo response limit is invalid");
        }
        this.timeout = timeout;
        this.maxResponseBytes = maxResponseBytes;
        this.clock = java.util.Objects.requireNonNull(clock);
        this.locationLabelFormatter = java.util.Objects.requireNonNull(locationLabelFormatter);
    }

    @Override
    public WildberriesGeoResult resolve(GeoCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("geo candidate must not be null");
        boolean acquired = false;
        try {
            accessCoordinator.awaitTurn();
            acquired = true;
            HttpRequest request = HttpRequest.newBuilder(uri(candidate))
                    .timeout(timeout)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                close(response.body());
                return WildberriesGeoResult.temporarilyUnavailable();
            }
            return parse(readBounded(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return WildberriesGeoResult.temporarilyUnavailable();
        } catch (ProviderCooldownActiveException | IOException | RuntimeException exception) {
            LOGGER.warn("Wildberries geo resolution failed, errorType={}", exception.getClass().getSimpleName());
            return WildberriesGeoResult.temporarilyUnavailable();
        } finally {
            if (acquired) accessCoordinator.completeAfter(Duration.ZERO);
        }
    }

    private WildberriesGeoResult parse(byte[] body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode xinfoNode = root.path("xinfo");
        if (!root.isObject() || !xinfoNode.isTextual() || xinfoNode.asText().isBlank()) {
            return WildberriesGeoResult.temporarilyUnavailable();
        }
        Map<String, String> parameters = parseQuery(xinfoNode.asText());
        try {
            String destinationValue = parameters.get("dest");
            String sppValue = parameters.get("spp");
            if (destinationValue == null || sppValue == null) {
                return WildberriesGeoResult.temporarilyUnavailable();
            }
            long destination = Long.parseLong(destinationValue);
            int spp = Integer.parseInt(sppValue);
            return WildberriesGeoResult.success(
                    new WildberriesLocationContext(destination, spp, clock.instant())
            );
        } catch (IllegalArgumentException exception) {
            return WildberriesGeoResult.temporarilyUnavailable();
        }
    }

    private Map<String, String> parseQuery(String value) {
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String part : value.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0) continue;
            String key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
            String parameterValue = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
            parameters.putIfAbsent(key, parameterValue);
        }
        return parameters;
    }

    private URI uri(GeoCandidate candidate) {
        String separator = endpoint.toString().contains("?") ? "&" : "?";
        return URI.create(endpoint + separator
                + "latitude=" + encode(candidate.getLatitude().toPlainString())
                + "&longitude=" + encode(candidate.getLongitude().toPlainString())
                + "&address=" + encode(address(candidate)));
    }

    private String address(GeoCandidate candidate) {
        String label = locationLabelFormatter.format(candidate);
        String country = candidate.getCountryName();
        if (label.toLowerCase(java.util.Locale.ROOT)
                .contains(country.toLowerCase(java.util.Locale.ROOT))) {
            return label;
        }
        return label + ", " + country;
    }

    private byte[] readBounded(InputStream input) throws IOException {
        try (input) {
            byte[] body = input.readNBytes(maxResponseBytes + 1);
            if (body.length > maxResponseBytes) throw new IOException("WB geo response is too large");
            return body;
        }
    }

    private void close(InputStream input) {
        try (input) { }
        catch (IOException ignored) { }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private URI validateEndpoint(URI value) {
        if (value == null || value.getHost() == null
                || !("https".equalsIgnoreCase(value.getScheme()) || isLoopback(value))) {
            throw new IllegalArgumentException("WB geo endpoint must use HTTPS");
        }
        return value;
    }

    private boolean isLoopback(URI value) {
        return "http".equalsIgnoreCase(value.getScheme())
                && ("localhost".equalsIgnoreCase(value.getHost()) || "127.0.0.1".equals(value.getHost()));
    }
}
