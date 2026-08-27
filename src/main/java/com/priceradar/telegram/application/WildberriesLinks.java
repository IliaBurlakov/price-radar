package com.priceradar.telegram.application;

import java.util.List;
import java.util.Optional;

public final class WildberriesLinks {

    private final Optional<String> sharedBasketUrl;
    private final List<WildberriesProductLink> productLinks;
    private final int invalidWildberriesUrlCount;
    private final boolean truncated;

    public WildberriesLinks(
            Optional<String> sharedBasketUrl,
            List<WildberriesProductLink> productLinks,
            int invalidWildberriesUrlCount,
            boolean truncated
    ) {
        this.sharedBasketUrl = sharedBasketUrl;
        this.productLinks = List.copyOf(productLinks);
        this.invalidWildberriesUrlCount = invalidWildberriesUrlCount;
        this.truncated = truncated;
    }

    public Optional<String> getSharedBasketUrl() {
        return sharedBasketUrl;
    }

    public List<WildberriesProductLink> getProductLinks() {
        return productLinks;
    }

    public int getInvalidWildberriesUrlCount() {
        return invalidWildberriesUrlCount;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
