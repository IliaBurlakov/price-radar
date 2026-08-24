package com.priceradar.sharedbasket.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PendingSharedBasketImportStore {

    PendingSharedBasketImport save(PendingSharedBasketImport pendingImport, Instant now);

    Optional<PendingSharedBasketImport> findOwned(UUID importId, UUID userId);

    void remove(UUID importId, UUID userId);
}
