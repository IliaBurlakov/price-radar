package com.priceradar.region.domain;

import java.util.Locale;

public final class GeoTextNormalizer {

    private GeoTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.forLanguageTag("ru"))
                .replace('ё', 'е');
    }
}
