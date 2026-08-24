package com.priceradar.sharedbasket.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class SharedBasketUrlParser {

    private static final Pattern SHARE_ID = Pattern.compile("[a-z0-9]{10}");

    public boolean supports(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return false;
        try {
            URI uri = new URI(rawUrl.trim());
            return "www.wildberries.ru".equalsIgnoreCase(uri.getHost())
                    && "/basket".equals(uri.getPath());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    public String parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw invalid();
        }
        try {
            URI uri = new URI(rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"www.wildberries.ru".equalsIgnoreCase(uri.getHost())
                    || uri.getPort() != -1
                    || !"/basket".equals(uri.getPath())
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw invalid();
            }
            List<String> shareIds = queryValues(uri.getRawQuery(), "shareId");
            if (shareIds.size() != 1 || !SHARE_ID.matcher(shareIds.getFirst()).matches()) {
                throw invalid();
            }
            return shareIds.getFirst();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private List<String> queryValues(String rawQuery, String expectedName) {
        List<String> values = new ArrayList<>();
        if (rawQuery == null || rawQuery.isBlank()) return values;
        for (String part : rawQuery.split("&", -1)) {
            String[] pair = part.split("=", 2);
            String name = decode(pair[0]);
            if (expectedName.equals(name)) {
                values.add(pair.length == 2 ? decode(pair[1]) : "");
            }
        }
        return values;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private InvalidSharedBasketUrlException invalid() {
        return new InvalidSharedBasketUrlException(
                "Expected https://www.wildberries.ru/basket?shareId=<10 lowercase letters or digits>"
        );
    }
}
