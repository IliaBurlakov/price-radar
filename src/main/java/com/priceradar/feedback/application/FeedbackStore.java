package com.priceradar.feedback.application;

import com.priceradar.feedback.domain.FeedbackMessage;

public interface FeedbackStore {
    void save(FeedbackMessage feedback);
}
