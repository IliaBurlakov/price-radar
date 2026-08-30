package com.priceradar.region.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PendingCitySelectionStore {

    PendingCitySelection replace(PendingCitySelection selection, Instant now);

    Optional<PendingCitySelection> findByUserId(UUID userId, Instant now);

    void remove(UUID userId);
}
