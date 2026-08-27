package com.priceradar.marketplace.wildberries;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "priceradar.provider.wildberries")
public final class WildberriesProviderProperties {

    private URI endpoint = URI.create("https://card.wb.ru/cards/v4/detail");
    private URI sharedBasketEndpoint = URI.create(
            "https://wbx-api-gateway.wildberries.ru/share-basket/api/v1/basket/"
    );
    private URI sharedBasketCardsEndpoint = URI.create("https://card.wb.ru/cards/v4/list");
    private URI geoEndpoint = URI.create("https://user-geo-data.wildberries.ru/get-geo-info");
    private Duration minRequestDelay = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Duration cacheTtl = Duration.ofMinutes(5);
    private int cacheMaxEntries = 10_000;
    private int batchSize = 50;
    private int maxAttempts = 3;
    private Duration baseBackoff = Duration.ofSeconds(2);
    private Duration maxBackoff = Duration.ofMinutes(1);
    private Duration maxRetryAfter = Duration.ofHours(24);
    private int maxResponseBytes = 2 * 1024 * 1024;
    private Duration geoTimeout = Duration.ofSeconds(10);
    private int geoMaxResponseBytes = 256 * 1024;
    private Duration rateLimitCooldown = Duration.ofMinutes(15);
    private Duration accessForbiddenCooldown = Duration.ofMinutes(30);
    private Duration serverErrorCooldown = Duration.ofMinutes(5);
    private Duration invalidResponseCooldown = Duration.ofMinutes(15);
    private Duration maxBackoffJitter = Duration.ofSeconds(1);

    public URI getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(URI endpoint) {
        this.endpoint = endpoint;
    }

    public URI getSharedBasketEndpoint() {
        return sharedBasketEndpoint;
    }

    public void setSharedBasketEndpoint(URI sharedBasketEndpoint) {
        this.sharedBasketEndpoint = sharedBasketEndpoint;
    }

    public URI getSharedBasketCardsEndpoint() {
        return sharedBasketCardsEndpoint;
    }

    public void setSharedBasketCardsEndpoint(URI sharedBasketCardsEndpoint) {
        this.sharedBasketCardsEndpoint = sharedBasketCardsEndpoint;
    }

    public URI getGeoEndpoint() { return geoEndpoint; }
    public void setGeoEndpoint(URI geoEndpoint) { this.geoEndpoint = geoEndpoint; }
    public Duration getGeoTimeout() { return geoTimeout; }
    public void setGeoTimeout(Duration geoTimeout) { this.geoTimeout = geoTimeout; }
    public int getGeoMaxResponseBytes() { return geoMaxResponseBytes; }
    public void setGeoMaxResponseBytes(int geoMaxResponseBytes) { this.geoMaxResponseBytes = geoMaxResponseBytes; }

    public Duration getMinRequestDelay() {
        return minRequestDelay;
    }

    public void setMinRequestDelay(Duration minRequestDelay) {
        this.minRequestDelay = minRequestDelay;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getBaseBackoff() {
        return baseBackoff;
    }

    public void setBaseBackoff(Duration baseBackoff) {
        this.baseBackoff = baseBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    public Duration getMaxRetryAfter() {
        return maxRetryAfter;
    }

    public void setMaxRetryAfter(Duration maxRetryAfter) {
        this.maxRetryAfter = maxRetryAfter;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public Duration getRateLimitCooldown() {
        return rateLimitCooldown;
    }

    public void setRateLimitCooldown(Duration rateLimitCooldown) {
        this.rateLimitCooldown = rateLimitCooldown;
    }

    public Duration getAccessForbiddenCooldown() {
        return accessForbiddenCooldown;
    }

    public void setAccessForbiddenCooldown(Duration accessForbiddenCooldown) {
        this.accessForbiddenCooldown = accessForbiddenCooldown;
    }

    public Duration getServerErrorCooldown() {
        return serverErrorCooldown;
    }

    public void setServerErrorCooldown(Duration serverErrorCooldown) {
        this.serverErrorCooldown = serverErrorCooldown;
    }

    public Duration getInvalidResponseCooldown() {
        return invalidResponseCooldown;
    }

    public void setInvalidResponseCooldown(Duration invalidResponseCooldown) {
        this.invalidResponseCooldown = invalidResponseCooldown;
    }

    public Duration getMaxBackoffJitter() {
        return maxBackoffJitter;
    }

    public void setMaxBackoffJitter(Duration maxBackoffJitter) {
        this.maxBackoffJitter = maxBackoffJitter;
    }
}
