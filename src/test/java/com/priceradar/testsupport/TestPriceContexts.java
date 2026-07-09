package com.priceradar.testsupport;

import com.priceradar.pricing.domain.PriceContext;

public final class TestPriceContexts {

    private TestPriceContexts() {
    }

    public static PriceContext moscow() {
        return new PriceContext("Moscow", 1_259_570_991L, 30);
    }
}
