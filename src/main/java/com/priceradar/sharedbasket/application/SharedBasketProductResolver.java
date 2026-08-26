package com.priceradar.sharedbasket.application;

import com.priceradar.pricing.domain.PriceContext;

import java.util.List;

public interface SharedBasketProductResolver {

    SharedBasketProductResolution resolveExact(
            List<SharedBasketItem> items,
            PriceContext priceContext
    );
}
