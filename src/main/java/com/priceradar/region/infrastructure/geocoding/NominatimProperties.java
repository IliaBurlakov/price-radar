package com.priceradar.region.infrastructure.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "priceradar.geocoding.nominatim")
public final class NominatimProperties {

    private URI baseUrl = URI.create("https://nominatim.openstreetmap.org/search");
    private Duration timeout = Duration.ofSeconds(10);
    private int maxResults = 8;
    private Duration minRequestInterval = Duration.ofSeconds(1);
    private String userAgent = "PriceRadar/0.1 (https://github.com/IliaBurlakov/price-radar)";
    private int maxResponseBytes = 512 * 1024;

    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    public Duration getMinRequestInterval() { return minRequestInterval; }
    public void setMinRequestInterval(Duration minRequestInterval) { this.minRequestInterval = minRequestInterval; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
}
