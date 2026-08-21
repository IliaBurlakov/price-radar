package com.priceradar.telegram.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramDisplayFormatterTest {

    @Test
    void hidesTechnicalZeroSizeButKeepsRealSize() {
        assertThat(TelegramDisplayFormatter.variant("Size: 0")).isEmpty();
        assertThat(TelegramDisplayFormatter.variant("Size: 42"))
                .contains("Размер: 42");
    }

    @Test
    void localizesKnownRegionForUserMessages() {
        assertThat(TelegramDisplayFormatter.region("Moscow")).isEqualTo("Москва");
    }
}
