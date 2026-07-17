package com.priceradar.telegram.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "priceradar.telegram")
public final class TelegramBotProperties {

    private boolean enabled = true;
    private URI apiBaseUrl = URI.create("https://api.telegram.org/");
    private Duration longPollingTimeout = Duration.ofSeconds(25);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private String botKey = "primary";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(URI apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public Duration getLongPollingTimeout() {
        return longPollingTimeout;
    }

    public void setLongPollingTimeout(Duration longPollingTimeout) {
        this.longPollingTimeout = longPollingTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getBotKey() {
        return botKey;
    }

    public void setBotKey(String botKey) {
        this.botKey = botKey;
    }
}
