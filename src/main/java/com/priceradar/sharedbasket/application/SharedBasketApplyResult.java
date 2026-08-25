package com.priceradar.sharedbasket.application;

import java.util.List;

public final class SharedBasketApplyResult {

    public enum Status {
        APPLIED, EXPIRED, USER_NOT_FOUND, REGION_MISMATCH,
        SYNCHRONIZATION_UNAVAILABLE, PLAN_CHANGED
    }

    private final Status status;
    private final int added;
    private final int kept;
    private final int ended;
    private final List<String> skippedByLimitTitles;

    private SharedBasketApplyResult(
            Status status,
            int added,
            int kept,
            int ended,
            List<String> skippedByLimitTitles
    ) {
        this.status = status;
        this.added = added;
        this.kept = kept;
        this.ended = ended;
        this.skippedByLimitTitles = List.copyOf(skippedByLimitTitles);
    }

    public static SharedBasketApplyResult applied(
            int added,
            int kept,
            int ended,
            List<String> skippedByLimitTitles
    ) {
        return new SharedBasketApplyResult(Status.APPLIED, added, kept, ended, skippedByLimitTitles);
    }

    public static SharedBasketApplyResult failed(Status status) {
        return new SharedBasketApplyResult(status, 0, 0, 0, List.of());
    }

    public Status getStatus() { return status; }
    public int getAdded() { return added; }
    public int getKept() { return kept; }
    public int getEnded() { return ended; }
    public int getSkippedByLimit() { return skippedByLimitTitles.size(); }
    public List<String> getSkippedByLimitTitles() { return skippedByLimitTitles; }
}
