package com.priceradar.testsupport;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ProxySelector;
import java.net.URI;
import org.junit.jupiter.api.Test;

class TestInfrastructureTest {

    @Test
    void blocksExternalHostsBeforeAnyConnectionIsAttempted() {
        assertThatThrownBy(
                () -> ProxySelector.getDefault().select(URI.create("https://www.wildberries.ru"))
        ).isInstanceOf(AssertionError.class)
                .hasMessageContaining("must not access external host");
    }
}
