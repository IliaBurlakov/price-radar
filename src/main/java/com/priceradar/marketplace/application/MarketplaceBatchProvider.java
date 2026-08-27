package com.priceradar.marketplace.application;

import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.domain.PriceContext;

import java.util.List;

public interface MarketplaceBatchProvider {

    MarketplaceBatchProviderResult resolveProducts(
            Marketplace marketplace,
            List<String> externalProductIds,
            PriceContext priceContext
    );
}
