package com.priceradar.tracking.application;

public final class ClearSubscriptionsResult {

    public enum Status { CLEARED, NOTHING_TO_CLEAR, PLAN_CHANGED, USER_NOT_FOUND }

    private final Status status;
    private final int ended;

    private ClearSubscriptionsResult(Status status, int ended) {
        this.status = status;
        this.ended = ended;
    }

    public static ClearSubscriptionsResult cleared(int ended) {
        return new ClearSubscriptionsResult(Status.CLEARED, ended);
    }

    public static ClearSubscriptionsResult of(Status status) {
        return new ClearSubscriptionsResult(status, 0);
    }

    public Status getStatus() { return status; }
    public int getEnded() { return ended; }
}
