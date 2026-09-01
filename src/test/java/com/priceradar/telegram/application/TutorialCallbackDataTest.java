package com.priceradar.telegram.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TutorialCallbackDataTest {

    @Test
    void encodesAndParsesSupportedTutorialActions() {
        assertCallback(TutorialCallbackData.open(TutorialTopic.PRODUCT),
                TutorialTopic.PRODUCT, TutorialCallbackData.Action.OPEN, null);
        assertCallback(TutorialCallbackData.open(TutorialTopic.BASKET),
                TutorialTopic.BASKET, TutorialCallbackData.Action.OPEN, null);
        assertCallback(TutorialCallbackData.show(TutorialTopic.PRODUCT, TutorialPlatform.IPHONE),
                TutorialTopic.PRODUCT, TutorialCallbackData.Action.SHOW, TutorialPlatform.IPHONE);
        assertCallback(TutorialCallbackData.show(TutorialTopic.PRODUCT, TutorialPlatform.ANDROID),
                TutorialTopic.PRODUCT, TutorialCallbackData.Action.SHOW, TutorialPlatform.ANDROID);
        assertCallback(TutorialCallbackData.show(TutorialTopic.BASKET, TutorialPlatform.WINDOWS),
                TutorialTopic.BASKET, TutorialCallbackData.Action.SHOW, TutorialPlatform.WINDOWS);
    }

    @Test
    void rejectsInvalidMalformedAndUnknownCallbacks() {
        assertThat(TutorialCallbackData.parse(null)).isEmpty();
        assertThat(TutorialCallbackData.parse("MENU:HOME")).isEmpty();
        assertThat(TutorialCallbackData.parse("TUTORIAL:PRODUCT")).isEmpty();
        assertThat(TutorialCallbackData.parse("TUTORIAL:PRODUCT:LINUX")).isEmpty();
        assertThat(TutorialCallbackData.parse("TUTORIAL:UNKNOWN:OPEN")).isEmpty();
        assertThat(TutorialCallbackData.parse("TUTORIAL:PRODUCT:IPHONE:EXTRA")).isEmpty();
    }

    private void assertCallback(
            String encoded,
            TutorialTopic topic,
            TutorialCallbackData.Action action,
            TutorialPlatform platform
    ) {
        TutorialCallbackData parsed = TutorialCallbackData.parse(encoded).orElseThrow();
        assertThat(parsed.getTopic()).isEqualTo(topic);
        assertThat(parsed.getAction()).isEqualTo(action);
        if (platform == null) {
            assertThat(parsed.getPlatform()).isEmpty();
        } else {
            assertThat(parsed.getPlatform()).contains(platform);
        }
    }
}
