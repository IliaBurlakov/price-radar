package com.priceradar.telegram.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramDisplayFormatterTest {

    @Test
    void freeSubscriptionSlotsNeverBecomeNegative() {
        assertThat(TelegramDisplayFormatter.trackingCapacity(7, 10))
                .isEqualTo("Отслеживается: 7 из 10 · можно добавить ещё 3");
        assertThat(TelegramDisplayFormatter.trackingCapacity(25, 10))
                .isEqualTo("Отслеживается: 10 из 10 · можно добавить ещё 0");
    }

    @Test
    void hidesTechnicalZeroSizeButKeepsRealSize() {
        assertThat(TelegramDisplayFormatter.variant("Size: 0")).isEmpty();
        assertThat(TelegramDisplayFormatter.variant("Size: 42"))
                .contains("Размер: 42");
        assertThat(TelegramDisplayFormatter.variant("Color: синий / Memory: 256 ГБ"))
                .contains("Цвет: синий / Память: 256 ГБ");
        assertThat(TelegramDisplayFormatter.variant("Белый"))
                .contains("Вариант: Белый");
    }

    @Test
    void localizesKnownRegionForUserMessages() {
        assertThat(TelegramDisplayFormatter.region("Moscow")).isEqualTo("Москва");
    }
}
