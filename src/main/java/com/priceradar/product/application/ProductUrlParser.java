package com.priceradar.product.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProductUrlParser {

    private static final String SUPPORTED_SCHEME = "https";
    private static final String SUPPORTED_HOST = "www.wildberries.ru";
    private static final Pattern PRODUCT_PATH = Pattern.compile("^/catalog/([^/]+)/detail\\.aspx$");
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9]\\d*");

    public ParsedProductUrl parse(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(InvalidProductUrlException.Reason.EMPTY, "Product URL must not be blank");
        }

        URI uri = parseUri(value.trim());
        validateSupportedUrl(uri);

        long nmId = parseProductId(uri.getRawPath());
        OptionalLong sizeId = parseSizeId(uri.getRawQuery());
        return new ParsedProductUrl(nmId, sizeId);
    }

    private static URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            throw invalid(InvalidProductUrlException.Reason.MALFORMED, "Product URL is malformed");
        }
    }

    private static void validateSupportedUrl(URI uri) {
        boolean supported = SUPPORTED_SCHEME.equalsIgnoreCase(uri.getScheme())
                && SUPPORTED_HOST.equalsIgnoreCase(uri.getHost())
                && uri.getPort() == -1
                && uri.getUserInfo() == null
                && uri.getFragment() == null;
        if (!supported) {
            throw invalid(
                    InvalidProductUrlException.Reason.UNSUPPORTED_URL,
                    "Only canonical HTTPS Wildberries product URLs are supported"
            );
        }
    }

    private static long parseProductId(String rawPath) {
        Matcher matcher = PRODUCT_PATH.matcher(rawPath == null ? "" : rawPath);
        if (!matcher.matches()) {
            throw invalid(
                    InvalidProductUrlException.Reason.UNSUPPORTED_URL,
                    "Unsupported Wildberries product URL path"
            );
        }
        return parsePositiveId(
                matcher.group(1),
                InvalidProductUrlException.Reason.INVALID_PRODUCT_ID,
                "nmId must be a positive integer"
        );
    }

    private static OptionalLong parseSizeId(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return OptionalLong.empty();
        }

        OptionalLong sizeId = OptionalLong.empty();
        for (String parameter : rawQuery.split("&", -1)) {
            String[] parts = parameter.split("=", 2);
            String name = decode(parts[0]);
            if (!"size".equals(name)) {
                continue;
            }
            if (sizeId.isPresent()) {
                throw invalid(
                        InvalidProductUrlException.Reason.INVALID_SIZE_ID,
                        "size query parameter must not be repeated"
                );
            }
            String value = parts.length == 2 ? decode(parts[1]) : "";
            sizeId = OptionalLong.of(parsePositiveId(
                    value,
                    InvalidProductUrlException.Reason.INVALID_SIZE_ID,
                    "sizeId must be a positive integer"
            ));
        }
        return sizeId;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalid(InvalidProductUrlException.Reason.MALFORMED, "Product URL query is malformed");
        }
    }

    private static long parsePositiveId(
            String value,
            InvalidProductUrlException.Reason reason,
            String message
    ) {
        if (!POSITIVE_ID.matcher(value).matches()) {
            throw invalid(reason, message);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid(reason, message);
        }
    }

    private static InvalidProductUrlException invalid(
            InvalidProductUrlException.Reason reason,
            String message
    ) {
        return new InvalidProductUrlException(reason, message);
    }
}
