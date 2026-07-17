package com.priceradar.marketplace.wildberries;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "priceradar.provider.wildberries")
public final class WildberriesProviderProperties {

    private URI endpoint = URI.create("https://card.wb.ru/cards/v4/detail");
    private Duration minRequestDelay = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Duration cacheTtl = Duration.ofMinutes(5);
    private int cacheMaxEntries = 10_000;
    private int maxAttempts = 3;
    private Duration baseBackoff = Duration.ofSeconds(2);

    public URI getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(URI endpoint) {
        this.endpoint = endpoint;
    }

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
}
