package com.priceradar.telegram.application;

import java.time.Instant;
import java.util.Optional;

public interface PendingTargetPriceStore {

    void put(PendingTargetPrice pending, Instant now);

    Optional<PendingTargetPrice> find(long telegramUserId, long chatId, Instant now);

    void remove(PendingTargetPrice pending);

    void remove(long telegramUserId, long chatId);
}
