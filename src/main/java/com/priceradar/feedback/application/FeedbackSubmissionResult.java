package com.priceradar.feedback.application;

import com.priceradar.feedback.domain.FeedbackMessage;

import java.util.Optional;

public final class FeedbackSubmissionResult {
    public enum Status { SUBMITTED, BLANK, TOO_LONG, EXPIRED, NOT_PENDING }

    private final Status status;
    private final FeedbackMessage feedback;

    private FeedbackSubmissionResult(Status status, FeedbackMessage feedback) {
        this.status = status;
        this.feedback = feedback;
    }

    public static FeedbackSubmissionResult of(Status status) {
        return new FeedbackSubmissionResult(status, null);
    }

    public static FeedbackSubmissionResult submitted(FeedbackMessage feedback) {
        return new FeedbackSubmissionResult(Status.SUBMITTED, feedback);
    }

    public Status getStatus() { return status; }
    public Optional<FeedbackMessage> getFeedback() { return Optional.ofNullable(feedback); }
}
