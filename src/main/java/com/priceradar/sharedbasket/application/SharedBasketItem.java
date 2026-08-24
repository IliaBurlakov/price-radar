package com.priceradar.sharedbasket.application;

import java.util.Objects;

public final class SharedBasketItem {

    private final long nmId;
    private final long chrtId;
    private final int quantity;

    public SharedBasketItem(long nmId, long chrtId, int quantity) {
        if (nmId <= 0 || chrtId <= 0) {
            throw new IllegalArgumentException("shared basket product and variant ids must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("shared basket quantity must be positive");
        }
        this.nmId = nmId;
        this.chrtId = chrtId;
        this.quantity = quantity;
    }

    public long getNmId() {
        return nmId;
    }

    public long getChrtId() {
        return chrtId;
    }

    public int getQuantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SharedBasketItem that)) return false;
        return nmId == that.nmId && chrtId == that.chrtId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nmId, chrtId);
    }
}
