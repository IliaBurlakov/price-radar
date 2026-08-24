package com.priceradar.marketplace.wildberries;

import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.product.application.VariantOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WildberriesCardMapperTest {

    private final WildberriesCardMapper mapper = new WildberriesCardMapper();

    @Test
    void classifiesInvalidAndMissingProviderResponses() {
        assertThat(mapper.map("   ", 123456789).getFailure()).get()
                .extracting(WildberriesMappingFailure::getCode)
                .isEqualTo(WildberriesMappingFailureCode.EMPTY_RESPONSE);
        assertThat(mapper.map("{not-json", 123456789).getFailure()).get()
                .extracting(WildberriesMappingFailure::getCode)
                .isEqualTo(WildberriesMappingFailureCode.MALFORMED_JSON);
        assertThat(mapper.map(
                fixture("wildberries/card-detail/product-not-found-card-v4-detail.json"),
                123456789
        ).getFailure()).get()
                .extracting(WildberriesMappingFailure::getCode)
                .isEqualTo(WildberriesMappingFailureCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void mapsCardV4SizesWithRegularMarketingAndBasicFallbackPrices() {
        WildberriesMappedProduct product = mapper.map(
                fixture("wildberries/card-detail/regular-card-v4-detail.json"),
                123456789
        ).getProduct().orElseThrow();

        assertThat(product.getNmId()).isEqualTo(123456789);
        assertThat(product.getTitle()).contains("Demo jacket");
        assertThat(product.getBrand()).contains("Demo Brand");

        List<VariantOption> options = product.getVariantOptions();
        assertThat(options).hasSize(2);

        VariantOption firstSize = options.getFirst();
        assertThat(firstSize.getVariantKey()).isEqualTo("SIZE:111");
        assertThat(firstSize.isAvailable()).isTrue();
        assertThat(firstSize.hasValidRegularPrice()).isTrue();
        assertThat(firstSize.getAttributes().getFirst().getName()).isEqualTo("Size");
        assertThat(firstSize.getAttributes().getFirst().getValue()).isEqualTo("42");

        ProviderPriceFields firstPrice = product.findPriceFields("SIZE:111").orElseThrow();
        assertThat(firstPrice.getProductPrice()).contains(RubleAmount.ofMinorUnits(89900));
        assertThat(firstPrice.getBasicPrice()).contains(RubleAmount.ofMinorUnits(129900));

        VariantOption secondSize = product.getVariantOptions().get(1);
        assertThat(secondSize.getVariantKey()).isEqualTo("SIZE:222");
        assertThat(secondSize.isAvailable()).isTrue();
        assertThat(secondSize.hasValidRegularPrice()).isFalse();

        ProviderPriceFields secondPrice = product.findPriceFields("SIZE:222").orElseThrow();
        assertThat(secondPrice.getProductPrice()).isEmpty();
        assertThat(secondPrice.getBasicPrice()).contains(RubleAmount.ofMinorUnits(119900));
    }

    @Test
    void mapsProductWithoutSizesToNoVariantPriceFields() {
        WildberriesMappedProduct product = mapper.map(
                fixture("wildberries/card-detail/no-sizes-card-v4-detail.json"),
                987654321
        ).getProduct().orElseThrow();

        assertThat(product.getNmId()).isEqualTo(987654321);
        assertThat(product.getVariantOptions()).isEmpty();

        ProviderPriceFields priceFields = product.getNoVariantPriceFields().orElseThrow();
        assertThat(priceFields.getProductPrice()).contains(RubleAmount.ofMinorUnits(77700));
        assertThat(priceFields.getBasicPrice()).contains(RubleAmount.ofMinorUnits(99900));
    }

    @Test
    void rejectsDuplicateSizeOptionIds() {
        String response = """
                {
                  "data": {
                    "products": [{
                      "id": 123456789,
                      "sizes": [
                        {"optionId": 111, "stocks": [{"qty": 1}], "price": {"product": 10000}},
                        {"optionId": 111, "stocks": [{"qty": 2}], "price": {"product": 9000}}
                      ]
                    }]
                  }
                }
                """;

        WildberriesMappingResult result = mapper.map(response, 123456789);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailure()).get()
                .extracting(WildberriesMappingFailure::getCode)
                .isEqualTo(WildberriesMappingFailureCode.SCHEMA_VIOLATION);
    }

    @Test
    void treatsStocksWithZeroQuantityAsUnavailable() {
        String response = """
                {
                  "data": {
                    "products": [{
                      "id": 123456789,
                      "sizes": [{
                        "optionId": 111,
                        "stocks": [{"qty": 0}],
                        "price": {"product": 10000}
                      }]
                    }]
                  }
                }
                """;

        WildberriesMappedProduct product = mapper.map(response, 123456789)
                .getProduct()
                .orElseThrow();

        assertThat(product.getVariantOptions().getFirst().isAvailable()).isFalse();
        assertThat(product.findPriceFields("SIZE:111").orElseThrow().isAvailable()).isFalse();
    }

    private String fixture(String path) {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Fixture not found: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
