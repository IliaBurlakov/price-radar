package com.priceradar.telegram.application;

import java.time.Duration;
import java.util.List;

public interface TelegramGateway {

    List<TelegramUpdate> receiveUpdates(long offset, Duration timeout);

    void sendMessage(OutgoingTelegramMessage message);
}
