package com.priceradar.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class VariantResolutionServiceTest {

    private final VariantResolutionService service = new VariantResolutionService();

    @Test
    void resolvesExplicitRequestedSizeByExactVariantKey() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.of(10));
        VariantAttribute size = new VariantAttribute("Size", "S");
        // Availability and missing price will be represented by PriceSnapshot later.
        // An explicitly requested user variant is resolved exactly and is never auto-selected.
        List<VariantOption> options = List.of(
                VariantOption.wildberriesSize(10, List.of(size), false, false),
                VariantOption.wildberriesSize(20, List.of(new VariantAttribute("Size", "M")), true, true)
        );

        ResolvedVariant result = service.resolveInitial(url, options).orElseThrow();

        assertThat(result.getVariantKey()).isEqualTo("SIZE:10");
        assertThat(result.getAttributes()).containsExactly(size);
        assertThat(result.isAutoSelected()).isFalse();
    }

    @Test
    void returnsEmptyWhenRequestedSizeIsMissingFromProviderOptions() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.of(10));
        List<VariantOption> options = List.of(
                VariantOption.wildberriesSize(
                        20,
                        List.of(new VariantAttribute("Size", "M")),
                        true,
                        true
                )
        );

        assertThat(service.resolveInitial(url, options)).isEmpty();
    }

    @Test
    void autoSelectsFirstAvailableOptionWithValidRegularPrice() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.empty());
        VariantOption first = VariantOption.providerOption(
                "sku-1",
                List.of(new VariantAttribute("Color", "Black")),
                true,
                true
        );
        VariantOption second = VariantOption.providerOption(
                "sku-2",
                List.of(new VariantAttribute("Color", "White")),
                true,
                true
        );

        ResolvedVariant result = service.resolveInitial(url, List.of(first, second)).orElseThrow();

        assertThat(result.getVariantKey()).isEqualTo("OPTION:sku-1");
        assertThat(result.isAutoSelected()).isTrue();
    }

    @Test
    void autoSelectionSkipsUnavailableOptions() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.empty());
        VariantOption unavailable = VariantOption.providerOption("sku-1", List.of(), false, true);
        VariantOption available = VariantOption.providerOption("sku-2", List.of(), true, true);

        ResolvedVariant result = service.resolveInitial(url, List.of(unavailable, available)).orElseThrow();

        assertThat(result.getVariantKey()).isEqualTo("OPTION:sku-2");
    }

    @Test
    void autoSelectionSkipsOptionsWithoutValidRegularPrice() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.empty());
        VariantOption withoutPrice = VariantOption.providerOption("sku-1", List.of(), true, false);
        VariantOption withPrice = VariantOption.providerOption("sku-2", List.of(), true, true);

        ResolvedVariant result = service.resolveInitial(url, List.of(withoutPrice, withPrice)).orElseThrow();

        assertThat(result.getVariantKey()).isEqualTo("OPTION:sku-2");
    }

    @Test
    void resolvesProductWithoutVariantsWhenOptionsAreEmpty() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.empty());

        ResolvedVariant result = service.resolveInitial(url, List.of()).orElseThrow();

        assertThat(result.getVariantKey()).isEqualTo("NO_VARIANT");
        assertThat(result.getAttributes()).isEmpty();
        assertThat(result.isAutoSelected()).isFalse();
    }

    @Test
    void returnsEmptyWhenOptionsExistButNoneIsEligible() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.empty());
        List<VariantOption> ineligibleOptions = List.of(
                VariantOption.providerOption("unavailable", List.of(), false, true),
                VariantOption.providerOption("without-price", List.of(), true, false)
        );

        assertThat(service.resolveInitial(url, ineligibleOptions)).isEmpty();
    }

    @Test
    void resolvesCompositeProviderVariantWithArbitraryAttributes() {
        ParsedProductUrl url = new ParsedProductUrl(123, OptionalLong.empty());
        List<VariantAttribute> attributes = List.of(
                new VariantAttribute("Color", "Black"),
                new VariantAttribute("Size", "L")
        );
        VariantOption composite = VariantOption.providerOption("black-l", attributes, true, true);

        ResolvedVariant result = service.resolveInitial(url, List.of(composite)).orElseThrow();

        assertThat(result.getVariantKey()).isEqualTo("OPTION:black-l");
        assertThat(result.getAttributes()).containsExactlyElementsOf(attributes);
        assertThat(result.getDisplayName()).contains("Color: Black / Size: L");
    }

    @Test
    void buildsDisplayNameForZeroOneAndMultipleAttributes() {
        ResolvedVariant empty = ResolvedVariant.noVariant();
        ResolvedVariant one = ResolvedVariant.providerOption(
                "single",
                List.of(new VariantAttribute("Storage", "256 GB")),
                true
        );
        ResolvedVariant multiple = ResolvedVariant.providerOption(
                "composite",
                List.of(
                        new VariantAttribute("Color", "Black"),
                        new VariantAttribute("RAM", "16 GB")
                ),
                true
        );

        assertThat(empty.getDisplayName()).isEmpty();
        assertThat(one.getDisplayName()).contains("Storage: 256 GB");
        assertThat(multiple.getDisplayName()).contains("Color: Black / RAM: 16 GB");
    }

    @Test
    void trimsAttributesAndProtectsAttributeCollectionsFromMutation() {
        VariantAttribute attribute = new VariantAttribute(" Color ", " Black ");
        List<VariantAttribute> source = new ArrayList<>();
        source.add(attribute);
        VariantOption option = VariantOption.providerOption("sku-1", source, true, true);

        source.clear();

        assertThat(attribute.getName()).isEqualTo("Color");
        assertThat(attribute.getValue()).isEqualTo("Black");
        assertThat(option.getAttributes()).containsExactly(attribute);
        assertThatThrownBy(() -> option.getAttributes().add(new VariantAttribute("Size", "L")))
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

    @Test
    void variantModelUsesRegularClassesInsteadOfRecords() {
        assertThat(VariantAttribute.class.isRecord()).isFalse();
        assertThat(VariantOption.class.isRecord()).isFalse();
        assertThat(ResolvedVariant.class.isRecord()).isFalse();
        assertThat(ParsedProductUrl.class.isRecord()).isFalse();
    }
}
