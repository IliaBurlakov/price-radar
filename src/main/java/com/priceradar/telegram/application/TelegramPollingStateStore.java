package com.priceradar.telegram.application;

import java.util.OptionalLong;

public interface TelegramPollingStateStore {

    OptionalLong findLastConfirmedUpdateId(String botKey);

    void confirm(String botKey, long updateId);

    int recordFailure(String botKey, long updateId);
}
