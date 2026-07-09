package com.priceradar.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ProductUrlParserTest {

    private final ProductUrlParser parser = new ProductUrlParser();

    @Test
    void parsesCanonicalProductUrlWithPositiveSizeAndIgnoresOtherParameters() {
        ParsedProductUrl result = parser.parse(
                "https://www.wildberries.ru/catalog/988004247/detail.aspx?targetUrl=MI&size=1479023782"
        );

        assertThat(result.getNmId()).isEqualTo(988_004_247L);
        assertThat(result.getRequestedSizeId()).isEqualTo(OptionalLong.of(1_479_023_782L));
    }

    @Test
    void parsesProductUrlWithoutSize() {
        ParsedProductUrl result = parser.parse(
                "https://www.wildberries.ru/catalog/604961294/detail.aspx?targetUrl=MI"
        );

        assertThat(result.getNmId()).isEqualTo(604_961_294L);
        assertThat(result.getRequestedSizeId()).isEmpty();
    }

    @Test
    void rejectsUnsupportedOrMalformedUrls() {
        assertReason("http://www.wildberries.ru/catalog/123/detail.aspx", InvalidProductUrlException.Reason.UNSUPPORTED_URL);
        assertReason("https://wildberries.ru/catalog/123/detail.aspx", InvalidProductUrlException.Reason.UNSUPPORTED_URL);
        assertReason("https://www.wildberries.ru/product/123", InvalidProductUrlException.Reason.UNSUPPORTED_URL);
        assertReason("not a URL", InvalidProductUrlException.Reason.MALFORMED);
    }

    @Test
    void rejectsInvalidOrAmbiguousIdentifiers() {
        assertReason(
                "https://www.wildberries.ru/catalog/0/detail.aspx",
                InvalidProductUrlException.Reason.INVALID_PRODUCT_ID
        );
        assertReason(
                "https://www.wildberries.ru/catalog/9223372036854775808/detail.aspx",
                InvalidProductUrlException.Reason.INVALID_PRODUCT_ID
        );
        assertReason(
                "https://www.wildberries.ru/catalog/123/detail.aspx?size=0",
                InvalidProductUrlException.Reason.INVALID_SIZE_ID
        );
        assertReason(
                "https://www.wildberries.ru/catalog/123/detail.aspx?size=10&size=11",
                InvalidProductUrlException.Reason.INVALID_SIZE_ID
        );
    }

    private void assertReason(String value, InvalidProductUrlException.Reason reason) {
        assertThatThrownBy(() -> parser.parse(value))
                .isInstanceOfSatisfying(
                        InvalidProductUrlException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason)
                );
    }
}
