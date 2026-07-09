package com.priceradar.user.domain;

import java.util.Objects;

public final class UserPricePreferences {

    private static final int DEFAULT_WALLET_DISCOUNT_PERCENT = 3;

    private final int walletDiscountPercent;

    public UserPricePreferences(int walletDiscountPercent) {
        if (walletDiscountPercent < 0 || walletDiscountPercent > 100) {
            throw new IllegalArgumentException("walletDiscountPercent must be between 0 and 100");
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
