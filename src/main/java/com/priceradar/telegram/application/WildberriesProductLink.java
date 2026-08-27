package com.priceradar.telegram.application;

import com.priceradar.product.application.ParsedProductUrl;

import java.util.Objects;

public final class WildberriesProductLink {

    private final String url;
    private final ParsedProductUrl parsedUrl;

    public WildberriesProductLink(String url, ParsedProductUrl parsedUrl) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        this.url = url;
        this.parsedUrl = Objects.requireNonNull(parsedUrl, "parsedUrl must not be null");
    }

    public String getUrl() {
        return url;
    }

    public ParsedProductUrl getParsedUrl() {
        return parsedUrl;
    }
}
