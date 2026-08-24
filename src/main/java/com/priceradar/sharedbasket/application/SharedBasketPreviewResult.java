package com.priceradar.sharedbasket.application;

import java.util.Optional;

public final class SharedBasketPreviewResult {

    public enum Status { READY, INVALID_URL, NOT_FOUND, TEMPORARILY_UNAVAILABLE }

    private final Status status;
    private final Optional<SharedBasketPreview> preview;

    private SharedBasketPreviewResult(Status status, Optional<SharedBasketPreview> preview) {
        this.status = status;
        this.preview = preview;
    }

    public static SharedBasketPreviewResult ready(SharedBasketPreview preview) {
        return new SharedBasketPreviewResult(Status.READY, Optional.of(preview));
    }

    public static SharedBasketPreviewResult failed(Status status) {
        return new SharedBasketPreviewResult(status, Optional.empty());
    }

    public Status getStatus() { return status; }
    public Optional<SharedBasketPreview> getPreview() { return preview; }
}
