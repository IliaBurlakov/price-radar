package com.priceradar.telegram.application;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PendingTargetPriceStore {

    private final ConcurrentMap<Long, PendingTargetPrice> pendingByUser = new ConcurrentHashMap<>();

    public void put(PendingTargetPrice pending) {
        if (pending == null) {
            throw new IllegalArgumentException("pending target price must not be null");
        }
        pendingByUser.put(pending.getTelegramUserId(), pending);
    }

    public Optional<PendingTargetPrice> find(long telegramUserId, long chatId) {
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("pending target lookup fields are invalid");
        }
        PendingTargetPrice pending = pendingByUser.get(telegramUserId);
        if (pending == null) {
            return Optional.empty();
        }
        if (pending.getChatId() != chatId) {
            pendingByUser.remove(telegramUserId, pending);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    public void remove(PendingTargetPrice pending) {
        if (pending == null) {
            throw new IllegalArgumentException("pending target price must not be null");
        }
        pendingByUser.remove(pending.getTelegramUserId(), pending);
    }
}
