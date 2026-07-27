package com.priceradar.product.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ResolvedQuoteStore {

    PersistedResolvedQuote save(UUID productId, ResolvedQuotePersistenceCommand command);

    Optional<Instant> findObservationTime(UUID snapshotId);
}
