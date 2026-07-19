package com.priceradar.product.application;

import java.util.UUID;

public interface ProductQuoteStore {

    UUID upsert(ResolvedQuotePersistenceCommand command);
}
