package com.priceradar.product.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariantResolutionServiceTest {

    private final VariantResolutionService service = new VariantResolutionService();

    @Test
    void resolvesOnlyTheExplicitlyRequestedSize() {
        ParsedProductUrl requested = new ParsedProductUrl(123, OptionalLong.of(10));
        VariantAttribute size = new VariantAttribute("Size", "S");
        List<VariantOption> options = List.of(
                // Availability and price status are recorded in PriceSnapshot later.
                // An explicit user choice must not be replaced by auto-selection.
                VariantOption.wildberriesSize(10, List.of(size), false, false),
                VariantOption.wildberriesSize(
                        20,
                        List.of(new VariantAttribute("Size", "M")),
                        true,
                        true
                )
        );

        ResolvedVariant result = service.resolveInitial(requested, options).orElseThrow();

        assertThat(result.getVariantKey()).isEqualTo("SIZE:10");
        assertThat(result.getAttributes()).containsExactly(size);
        assertThat(result.isAutoSelected()).isFalse();
        assertThat(service.resolveInitial(
                new ParsedProductUrl(123, OptionalLong.of(30)),
                options
        )).isEmpty();
    }

    @Test
    void autoSelectsTheFirstAvailableOptionWithARegularPrice() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.empty());
        List<VariantOption> options = List.of(
                VariantOption.providerOption("unavailable", List.of(), false, true),
                VariantOption.providerOption("without-price", List.of(), true, false),
                VariantOption.providerOption("first-eligible", List.of(), true, true),
                VariantOption.providerOption("second-eligible", List.of(), true, true)
        );

        ResolvedVariant result = service.resolveInitial(url, options).orElseThrow();

        assertThat(result.getVariantKey()).isEqualTo("OPTION:first-eligible");
        assertThat(result.isAutoSelected()).isTrue();
    }

    @Test
    void distinguishesProductsWithoutVariantsFromProductsWithoutEligibleOptions() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.empty());

        ResolvedVariant noVariant = service.resolveInitial(url, List.of()).orElseThrow();
        List<VariantOption> ineligibleOptions = List.of(
                VariantOption.providerOption("unavailable", List.of(), false, true),
                VariantOption.providerOption("without-price", List.of(), true, false)
        );

        assertThat(noVariant.getVariantKey()).isEqualTo("NO_VARIANT");
        assertThat(noVariant.getAttributes()).isEmpty();
        assertThat(noVariant.isAutoSelected()).isFalse();
        assertThat(service.resolveInitial(url, ineligibleOptions)).isEmpty();
    }

    @Test
    void keepsArbitraryVariantAttributesStableAndImmutable() {
        List<VariantAttribute> source = new ArrayList<>();
        source.add(new VariantAttribute(" Color ", " Black "));
        source.add(new VariantAttribute("Size", "L"));
        VariantOption option = VariantOption.providerOption("black-l", source, true, true);
        source.clear();

        ResolvedVariant result = service.resolveInitial(
                new ParsedProductUrl(123, OptionalLong.empty()),
                List.of(option)
        ).orElseThrow();

        assertThat(result.getVariantKey()).isEqualTo("OPTION:black-l");
        assertThat(result.getDisplayName()).contains("Color: Black / Size: L");
        assertThat(result.getAttributes()).hasSize(2);
        assertThatThrownBy(() -> result.getAttributes().add(new VariantAttribute("RAM", "16 GB")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidAttributesAndVariantKeys() {
        assertThatThrownBy(() -> new VariantAttribute(null, "Black"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VariantAttribute("Color", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VariantOption("SIZE:0", List.of(), true, true))
                .isInstanceOf(IllegalArgumentException.class);

        List<VariantAttribute> attributesWithNull = new ArrayList<>();
        attributesWithNull.add(null);
        assertThatThrownBy(() -> new VariantOption("NO_VARIANT", attributesWithNull, true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
