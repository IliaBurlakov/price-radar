package com.priceradar.region.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "priceradar.region-selection")
public final class RegionSelectionProperties {

    private Duration sessionTtl = Duration.ofMinutes(15);

    public Duration getSessionTtl() { return sessionTtl; }
    public void setSessionTtl(Duration sessionTtl) { this.sessionTtl = sessionTtl; }
}
