package com.priceradar.user.domain;

import java.util.Objects;

public final class UserPricePreferences {

    public static final int MIN_WALLET_DISCOUNT_PERCENT = 2;
    public static final int MAX_WALLET_DISCOUNT_PERCENT = 20;
    private static final int DEFAULT_WALLET_DISCOUNT_PERCENT = 3;

    private final int walletDiscountPercent;

    public UserPricePreferences(int walletDiscountPercent) {
        if (walletDiscountPercent < MIN_WALLET_DISCOUNT_PERCENT
                || walletDiscountPercent > MAX_WALLET_DISCOUNT_PERCENT) {
            throw new IllegalArgumentException("walletDiscountPercent must be between 2 and 20");
        }
        this.walletDiscountPercent = walletDiscountPercent;
    }

    public static UserPricePreferences defaults() {
        return new UserPricePreferences(DEFAULT_WALLET_DISCOUNT_PERCENT);
    }

    public int getWalletDiscountPercent() {
        return walletDiscountPercent;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserPricePreferences that)) {
            return false;
        }
        return walletDiscountPercent == that.walletDiscountPercent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(walletDiscountPercent);
    }

    @Override
    public String toString() {
        return "UserPricePreferences{" +
                "walletDiscountPercent=" + walletDiscountPercent +
                '}';
    }
}
