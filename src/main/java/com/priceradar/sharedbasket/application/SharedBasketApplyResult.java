package com.priceradar.sharedbasket.application;

public final class SharedBasketApplyResult {

    public enum Status {
        APPLIED, EXPIRED, USER_NOT_FOUND, REGION_MISMATCH,
        SYNCHRONIZATION_UNAVAILABLE, PLAN_CHANGED
    }

    private final Status status;
    private final int added;
    private final int kept;
    private final int ended;
    private final int skippedByLimit;

    private SharedBasketApplyResult(Status status, int added, int kept, int ended, int skippedByLimit) {
        this.status = status;
        this.added = added;
        this.kept = kept;
        this.ended = ended;
        this.skippedByLimit = skippedByLimit;
    }

    public static SharedBasketApplyResult applied(int added, int kept, int ended, int skippedByLimit) {
        return new SharedBasketApplyResult(Status.APPLIED, added, kept, ended, skippedByLimit);
    }

    public static SharedBasketApplyResult failed(Status status) {
        return new SharedBasketApplyResult(status, 0, 0, 0, 0);
    }

    public Status getStatus() { return status; }
    public int getAdded() { return added; }
    public int getKept() { return kept; }
    public int getEnded() { return ended; }
    public int getSkippedByLimit() { return skippedByLimit; }
}
