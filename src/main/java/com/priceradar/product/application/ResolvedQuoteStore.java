package com.priceradar.product.application;

import java.util.UUID;

public interface ResolvedQuoteStore {

    PersistedResolvedQuote save(UUID productId, ResolvedQuotePersistenceCommand command);
}
