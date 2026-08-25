package com.priceradar.telegram.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingCallbackCodecTest {

    private final TrackingCallbackCodec codec = new TrackingCallbackCodec(
            "test-only-callback-secret-with-more-than-32-bytes"
    );

    @Test
    void callbackIsAcceptedOnlyForItsTelegramUser() {
        UUID quoteSnapshotId = UUID.randomUUID();
        String callback = codec.encode(
                TrackingCallbackData.Action.TRACK_ANY_DECREASE,
                quoteSnapshotId,
                1001L
        );

        assertThat(codec.decode(callback, 1001L))
                .get()
                .extracting(TrackingCallbackData::getQuoteSnapshotId)
                .isEqualTo(quoteSnapshotId);
        assertThat(codec.decode(callback, 1002L)).isEmpty();
    }

    @Test
    void signedCallbackFitsTelegramLimitAndRejectsTampering() {
        String callback = codec.encode(
                TrackingCallbackData.Action.TRACK_ANY_DECREASE,
                UUID.randomUUID(),
                1001L
        );
        String tampered = callback.substring(0, callback.length() - 1) + "A";

        assertThat(callback.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(64);
        assertThat(codec.decode(tampered, 1001L)).isEmpty();

        String cancelCallback = codec.encode(
                TrackingCallbackData.Action.CANCEL_TARGET,
                UUID.randomUUID(),
                1001L
        );
        assertThat(cancelCallback.getBytes(StandardCharsets.UTF_8))
                .hasSizeLessThanOrEqualTo(64);
    }
}
