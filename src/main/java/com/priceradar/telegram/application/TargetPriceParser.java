package com.priceradar.telegram.application;

import com.priceradar.pricing.domain.RubleAmount;

import java.util.Optional;

public final class TargetPriceParser {

    public Optional<RubleAmount> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String normalized = text.trim()
                .replace(" ", "")
                .replace("\u00A0", "")
                .replace("\u202F", "");
        if (!normalized.matches("[0-9]+([.,][0-9]{1,2})?")) {
            return Optional.empty();
        }

        int separator = Math.max(normalized.indexOf('.'), normalized.indexOf(','));
        String wholePart = separator < 0 ? normalized : normalized.substring(0, separator);
        String fractionPart = separator < 0 ? "" : normalized.substring(separator + 1);
        try {
            long rubles = Long.parseLong(wholePart);
            long kopecks = fractionPart.isEmpty()
                    ? 0
                    : Long.parseLong(fractionPart) * (fractionPart.length() == 1 ? 10 : 1);
            long minorUnits = Math.addExact(Math.multiplyExact(rubles, 100), kopecks);
            if (minorUnits <= 0) {
                return Optional.empty();
            }
            return Optional.of(RubleAmount.ofMinorUnits(minorUnits));
        } catch (NumberFormatException | ArithmeticException exception) {
            return Optional.empty();
        }
    }
}
