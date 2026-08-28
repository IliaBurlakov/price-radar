package com.priceradar.feedback.application;

import java.util.Optional;

public interface PendingFeedbackInputStore {
    void replace(PendingFeedbackInput pending);
    Optional<PendingFeedbackInput> find(long telegramUserId, long chatId);
    void remove(long telegramUserId, long chatId);
}
