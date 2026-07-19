package com.priceradar.user.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "priceradar.user.defaults")
public final class UserDefaultsProperties {

    private String cityName;
    private long dest;
    private int spp;
    private int walletDiscountPercent;

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public long getDest() {
        return dest;
    }

    public void setDest(long dest) {
        this.dest = dest;
    }

    public int getSpp() {
        return spp;
    }

    public void setSpp(int spp) {
        this.spp = spp;
    }

    public int getWalletDiscountPercent() {
        return walletDiscountPercent;
    }

    public void setWalletDiscountPercent(int walletDiscountPercent) {
        this.walletDiscountPercent = walletDiscountPercent;
    }
}
