package com.priceradar.user.application;

import java.util.Optional;

public interface PendingWalletDiscountInputStore {

    void replace(PendingWalletDiscountInput pending);

    Optional<PendingWalletDiscountInput> find(long telegramUserId, long chatId);

    void remove(long telegramUserId, long chatId);
}
