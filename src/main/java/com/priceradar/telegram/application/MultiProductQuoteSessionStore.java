package com.priceradar.telegram.application;

import java.util.Optional;
import java.util.UUID;

public interface MultiProductQuoteSessionStore {

    void save(MultiProductQuoteSession session);

    Optional<MultiProductQuoteSession> findOwned(UUID sessionId, UUID userId);
}
