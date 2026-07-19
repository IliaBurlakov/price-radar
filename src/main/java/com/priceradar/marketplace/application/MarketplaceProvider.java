package com.priceradar.marketplace.application;

import com.priceradar.marketplace.domain.Marketplace;

public interface MarketplaceProvider {

    Marketplace getMarketplace();

    MarketplaceProviderResult resolveProduct(MarketplaceProductRequest request);

    MarketplaceProviderResult fetchCurrent(MarketplaceCurrentProductRequest request);
}
