package com.priceradar.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.priceradar.marketplace.domain.Marketplace;
import org.junit.jupiter.api.Test;

class WatchKeyTest {

    @Test
    void supportsEveryStableVariantKind() {
        WatchKey sized = WatchKey.forSize(Marketplace.WILDBERRIES, 123, 456, 1_259_570_991L, 30);
        WatchKey option = WatchKey.forProviderOption(
                Marketplace.WILDBERRIES, 123, "789", 1_259_570_991L, 30
        );
        WatchKey noSize = WatchKey.withoutSize(Marketplace.WILDBERRIES, 123, 1_259_570_991L, 30);

        assertThat(sized.variantKey()).isEqualTo("SIZE:456");
        assertThat(sized.variantKind()).isEqualTo(VariantKind.SIZE);
        assertThat(option.variantKey()).isEqualTo("OPTION:789");
        assertThat(option.variantKind()).isEqualTo(VariantKind.PROVIDER_OPTION);
        assertThat(noSize.variantKey()).isEqualTo("NO_SIZE");
        assertThat(noSize.variantKind()).isEqualTo(VariantKind.NO_SIZE);
    }

    @Test
    void rejectsMissingOrUnstableVariantIdentifier() {
        assertThatNullPointerException().isThrownBy(
                () -> new WatchKey(Marketplace.WILDBERRIES, 123, null, 1_259_570_991L, 30)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new WatchKey(Marketplace.WILDBERRIES, 123, "", 1_259_570_991L, 30)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new WatchKey(Marketplace.WILDBERRIES, 123, "SIZE:0", 1_259_570_991L, 30)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new WatchKey(Marketplace.WILDBERRIES, 123, "OPTION: ", 1_259_570_991L, 30)
        );
    }

    @Test
    void includesRegionAndVariantInIdentity() {
        WatchKey moscow = WatchKey.forSize(
                Marketplace.WILDBERRIES, 123, 456, 1_259_570_991L, 30
        );
        WatchKey novosibirsk = WatchKey.forSize(
                Marketplace.WILDBERRIES, 123, 456, -366_519L, 30
        );
        WatchKey otherSize = WatchKey.forSize(
                Marketplace.WILDBERRIES, 123, 789, 1_259_570_991L, 30
        );

        assertThat(moscow).isNotEqualTo(novosibirsk).isNotEqualTo(otherSize);
    }
}
