package com.priceradar.user.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "priceradar.user.defaults")
public final class UserDefaultsProperties {

    private int walletDiscountPercent;

    public int getWalletDiscountPercent() {
        return walletDiscountPercent;
    }

    public void setWalletDiscountPercent(int walletDiscountPercent) {
        this.walletDiscountPercent = walletDiscountPercent;
    }
}
