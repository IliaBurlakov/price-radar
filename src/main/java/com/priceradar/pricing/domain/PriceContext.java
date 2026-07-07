package com.priceradar.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record PriceContext(
        String cityName,
        long dest,
        int spp,
        BigDecimal walletDiscountPercent
) {

    public static final String DEFAULT_CITY = "Moscow";
    public static final long MOSCOW_DEST = 1_259_570_991L;
    public static final int MOSCOW_SPP = 30;
    public static final String TESTED_CITY = "Novosibirsk";
    public static final long NOVOSIBIRSK_DEST = -366_519L;
    public static final int NOVOSIBIRSK_SPP = 30;
    public static final BigDecimal DEFAULT_WALLET_DISCOUNT_PERCENT = new BigDecimal("3");

    public PriceContext {
        Objects.requireNonNull(cityName, "cityName must not be null");
        cityName = cityName.trim();
        if (cityName.isEmpty()) {
            throw new IllegalArgumentException("cityName must not be blank");
        }
        if (dest == 0) {
            throw new IllegalArgumentException("dest must not be zero");
        }
        if (spp < 0 || spp > 100) {
            throw new IllegalArgumentException("spp must be between 0 and 100");
        }
        Objects.requireNonNull(walletDiscountPercent, "walletDiscountPercent must not be null");
        if (walletDiscountPercent.compareTo(BigDecimal.ZERO) < 0
                || walletDiscountPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("walletDiscountPercent must be between 0 and 100");
        }
        walletDiscountPercent = walletDiscountPercent.stripTrailingZeros();
    }

    public static PriceContext moscow() {
        return moscow(DEFAULT_WALLET_DISCOUNT_PERCENT);
    }

    public static PriceContext moscow(BigDecimal walletDiscountPercent) {
        return new PriceContext(DEFAULT_CITY, MOSCOW_DEST, MOSCOW_SPP, walletDiscountPercent);
    }

    public static PriceContext novosibirsk(BigDecimal walletDiscountPercent) {
        return new PriceContext(TESTED_CITY, NOVOSIBIRSK_DEST, NOVOSIBIRSK_SPP, walletDiscountPercent);
    }
}
