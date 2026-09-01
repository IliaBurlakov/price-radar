package com.priceradar.telegram.application;

import com.priceradar.product.application.InvalidProductUrlException;
import com.priceradar.product.application.ParsedProductUrl;
import com.priceradar.product.application.ProductUrlParser;
import com.priceradar.sharedbasket.application.InvalidSharedBasketUrlException;
import com.priceradar.sharedbasket.application.SharedBasketUrlParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WildberriesLinkExtractor {

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING_PUNCTUATION = ".,;:)]}!\"'»”";
    private static final String ATTACHED_TEXT_SEPARATORS = ",;)]}!\"'»”";

    private final ProductUrlParser productUrlParser;
    private final SharedBasketUrlParser sharedBasketUrlParser;
    private final int maxProductLinks;

    public WildberriesLinkExtractor(
            ProductUrlParser productUrlParser,
            SharedBasketUrlParser sharedBasketUrlParser,
            int maxProductLinks
    ) {
        this.productUrlParser = Objects.requireNonNull(productUrlParser);
        this.sharedBasketUrlParser = Objects.requireNonNull(sharedBasketUrlParser);
        if (maxProductLinks <= 0) {
            throw new IllegalArgumentException("product link limit must be positive");
        }
        this.maxProductLinks = maxProductLinks;
    }

    public WildberriesLinks extract(String text) {
        if (text == null || text.isBlank()) {
            return new WildberriesLinks(Optional.empty(), List.of(), 0, false);
        }

        String firstBasketUrl = null;
        Map<ParsedProductUrl, WildberriesProductLink> uniqueProducts = new LinkedHashMap<>();
        int invalidWildberriesUrls = 0;
        boolean truncated = false;

        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String candidate = cleanCandidate(matcher.group());

            try {
                sharedBasketUrlParser.parse(candidate);
                if (firstBasketUrl == null) {
                    firstBasketUrl = candidate;
                }
                continue;
            } catch (InvalidSharedBasketUrlException ignored) {
                // A candidate can still be a product URL.
            }

            try {
                ParsedProductUrl parsedUrl = productUrlParser.parse(candidate);
                if (!uniqueProducts.containsKey(parsedUrl)) {
                    if (uniqueProducts.size() < maxProductLinks) {
                        uniqueProducts.put(parsedUrl, new WildberriesProductLink(candidate, parsedUrl));
                    } else {
                        truncated = true;
                    }
                }
            } catch (InvalidProductUrlException ignored) {
                if (hasWildberriesHost(candidate)) {
                    invalidWildberriesUrls++;
                }
            }
        }

        if (firstBasketUrl != null) {
            return new WildberriesLinks(
                    Optional.of(firstBasketUrl), List.of(), invalidWildberriesUrls, false
            );
        }
        return new WildberriesLinks(
                Optional.empty(), new ArrayList<>(uniqueProducts.values()), invalidWildberriesUrls, truncated
        );
    }

    private String cleanCandidate(String value) {
        int separator = firstAttachedTextSeparator(value);
        if (separator >= 0) {
            value = value.substring(0, separator);
        }
        int end = value.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    private int firstAttachedTextSeparator(String value) {
        int queryStart = value.indexOf('?');
        int searchFrom = queryStart >= 0 ? queryStart + 1 : Math.min(value.length(), "https://".length());
        int first = -1;
        for (int index = searchFrom; index < value.length(); index++) {
            if (ATTACHED_TEXT_SEPARATORS.indexOf(value.charAt(index)) >= 0) {
                first = index;
                break;
            }
        }
        return first;
    }

    private boolean hasWildberriesHost(String value) {
        try {
            String host = new URI(value).getHost();
            return "wildberries.ru".equalsIgnoreCase(host)
                    || "www.wildberries.ru".equalsIgnoreCase(host);
        } catch (URISyntaxException exception) {
            String normalized = value.toLowerCase(Locale.ROOT);
            return normalized.startsWith("https://wildberries.ru/")
                    || normalized.startsWith("http://wildberries.ru/")
                    || normalized.startsWith("https://www.wildberries.ru/")
                    || normalized.startsWith("http://www.wildberries.ru/");
        }
    }
}
