package com.priceradar.telegram.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedBasketCallbackCodecTest {

    @Test
    void authenticatesOpaqueImportIdForOneTelegramUser() {
        SharedBasketCallbackCodec codec = new SharedBasketCallbackCodec(
                "01234567890123456789012345678901"
        );
        UUID importId = UUID.randomUUID();
        String encoded = codec.encodeSyncConfirm(importId, "AbCdEf12345", 7001L);

        assertThat(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(64);
        assertThat(codec.decode(encoded, 7001L)).get().satisfies(decoded -> {
            assertThat(decoded.getAction()).isEqualTo(SharedBasketCallbackCodec.Action.SYNC_CONFIRM);
            assertThat(decoded.getImportId()).isEqualTo(importId);
            assertThat(decoded.getPlanFingerprint()).contains("AbCdEf12345");
        });
        assertThat(codec.decode(encoded, 7002L)).isEmpty();
        assertThat(codec.decode(encoded.substring(0, encoded.length() - 1) + "x", 7001L)).isEmpty();
        assertThatThrownBy(() -> codec.encode(
                SharedBasketCallbackCodec.Action.SYNC_CONFIRM, importId, 7001L
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
