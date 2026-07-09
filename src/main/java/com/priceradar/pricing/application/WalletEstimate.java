package com.priceradar.pricing.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.WalletPriceSource;

import java.util.Objects;

public final class WalletEstimate {

    private final RubleAmount amount;
    private final int walletDiscountPercent;
    private final WalletPriceSource source;

    public WalletEstimate(RubleAmount amount, int walletDiscountPercent, WalletPriceSource source) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (walletDiscountPercent < 0 || walletDiscountPercent > 100) {
            throw new IllegalArgumentException("walletDiscountPercent must be between 0 and 100");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        this.amount = amount;
        this.walletDiscountPercent = walletDiscountPercent;
        this.source = source;
    }

    public RubleAmount getAmount() {
        return amount;
    }

    public int getWalletDiscountPercent() {
        return walletDiscountPercent;
    }

    public WalletPriceSource getSource() {
        return source;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WalletEstimate that)) {
            return false;
        }
        return walletDiscountPercent == that.walletDiscountPercent
                && Objects.equals(amount, that.amount)
                && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, walletDiscountPercent, source);
    }

    @Override
    public String toString() {
        return "WalletEstimate{" +
                "amount=" + amount +
                ", walletDiscountPercent=" + walletDiscountPercent +
                ", source=" + source +
                '}';
    }
}
