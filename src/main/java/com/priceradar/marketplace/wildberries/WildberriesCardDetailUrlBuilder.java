package com.priceradar.marketplace.wildberries;

import com.priceradar.pricing.domain.PriceContext;

import java.net.URI;

public final class WildberriesCardDetailUrlBuilder {
    private final URI baseUri;

    public WildberriesCardDetailUrlBuilder(URI baseUri) {
        if (baseUri == null)
            throw new IllegalArgumentException("baseUri must not be null!");

        String scheme = baseUri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("https")
                && !(scheme.equalsIgnoreCase("http") && baseUri.getHost() != null
                && isLoopbackHost(baseUri.getHost()))))
            throw new IllegalArgumentException("baseUri must use https, except for loopback testing");

        if (baseUri.getQuery() != null) {
            throw new IllegalArgumentException("baseUri must not contain query parameters");
        }

        if (baseUri.getFragment() != null) {
            throw new IllegalArgumentException("baseUri must not contain a fragment");
        }

        if (!baseUri.isAbsolute()) {
            throw new IllegalArgumentException("baseUri must be absolute");
        }

        if (baseUri.getHost() == null || baseUri.getHost().isBlank()) {
            throw new IllegalArgumentException("baseUri must contain a host");
        }
        if (!baseUri.getHost().equalsIgnoreCase("card.wb.ru")
                && !isLoopbackHost(baseUri.getHost())) {
            throw new IllegalArgumentException("baseUri host must be card.wb.ru");
        }
        if (baseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("baseUri must not contain user info");
        }

        this.baseUri = baseUri;
    }

    public URI build(long nmId, PriceContext priceContext) {
        if (nmId <= 0)
            throw new IllegalArgumentException("nmId must be positive!");
        if (priceContext == null)
            throw new IllegalArgumentException("priceContext must not be null");
        return build(nmId, priceContext.getDest(), priceContext.getSpp());
    }

    private boolean isLoopbackHost(String host) {
        return host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("[::1]");
    }

    public URI build(long nmId, long dest, int spp) {
        if (nmId <= 0)
            throw new IllegalArgumentException("nmId must be positive!");
        if (dest == 0)
            throw new IllegalArgumentException("dest must not be zero!");
        if (spp < 0)
            throw new IllegalArgumentException("spp must be non-negative!");
        String query = String.format("appType=1&curr=rub&dest=%d&spp=%d&nm=%d", dest, spp, nmId);
        return URI.create(baseUri.toString() + "?" + query);
    }
}
