package com.priceradar.sharedbasket.application;

public final class PendingUnavailableSharedBasketItem {

    private final int position;
    private final String displayName;

    public PendingUnavailableSharedBasketItem(int position, String displayName) {
        if (position < 0 || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("pending unavailable basket item fields are invalid");
        }
        this.position = position;
        this.displayName = displayName.trim();
    }

    public int getPosition() {
        return position;
    }

    public String getDisplayName() {
        return displayName;
    }
}
