package com.priceradar.pricing.application;

import com.priceradar.pricing.domain.RubleAmount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

public final class RublePriceFormatter {

    private static final BigDecimal MINOR_UNITS_PER_RUBLE = BigDecimal.valueOf(100);
    private static final Locale RUSSIAN_LOCALE = Locale.forLanguageTag("ru-RU");

    private RublePriceFormatter() {
    }

    public static String format(RubleAmount amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        return formatMinorUnits(BigDecimal.valueOf(amount.getMinorUnits()));
    }

    public static String formatMinorUnits(BigDecimal minorUnits) {
        Objects.requireNonNull(minorUnits, "minorUnits must not be null");
        if (minorUnits.signum() < 0) {
            throw new IllegalArgumentException("minorUnits must be non-negative");
        }
        long roundedRubles = minorUnits
                .divide(MINOR_UNITS_PER_RUBLE, 0, RoundingMode.HALF_UP)
                .longValueExact();
        return String.format(RUSSIAN_LOCALE, "%,d ₽", roundedRubles);
    }
}
