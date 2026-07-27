package com.priceradar.product.application;

import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class ResolvedQuotePersistenceService {

    private final ProductQuoteStore productStore;
    private final ResolvedQuoteStore quoteStore;

    public ResolvedQuotePersistenceService(
            ProductQuoteStore productStore,
            ResolvedQuoteStore quoteStore
    ) {
        if (productStore == null || quoteStore == null) {
            throw new IllegalArgumentException("resolved quote persistence dependencies must not be null");
        }
        this.productStore = productStore;
        this.quoteStore = quoteStore;
    }

    @Transactional
    public PersistedResolvedQuote save(ResolvedQuotePersistenceCommand command) {
        UUID productId = productStore.upsert(command);
        return quoteStore.save(productId, command);
    }
}
