package com.priceradar.telegram.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class TrackingCallbackCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_BYTES = 6;
    private static final int TELEGRAM_CALLBACK_LIMIT_BYTES = 64;

    private final SecretKeySpec signingKey;

    public TrackingCallbackCodec(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("tracking callback secret must not be blank");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("tracking callback secret must contain at least 32 bytes");
        }
        this.signingKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    public String encode(
            TrackingCallbackData.Action action,
            UUID quoteSnapshotId,
            long telegramUserId
    ) {
        validateIdentity(action, quoteSnapshotId, telegramUserId);
        String unsigned = action.name() + ":" + quoteSnapshotId;
        String encoded = unsigned + ":" + signature(unsigned, telegramUserId);
        if (encoded.getBytes(StandardCharsets.UTF_8).length > TELEGRAM_CALLBACK_LIMIT_BYTES) {
            throw new IllegalStateException("signed tracking callback exceeds Telegram limit");
        }
        return encoded;
    }

    public Optional<TrackingCallbackData> decode(String data, long telegramUserId) {
        if (data == null || telegramUserId <= 0
                || data.getBytes(StandardCharsets.UTF_8).length > TELEGRAM_CALLBACK_LIMIT_BYTES) {
            return Optional.empty();
        }
        String[] parts = data.split(":", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            TrackingCallbackData.Action action = TrackingCallbackData.Action.valueOf(parts[0]);
            UUID quoteSnapshotId = UUID.fromString(parts[1]);
            if (!quoteSnapshotId.toString().equals(parts[1].toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            String unsigned = parts[0] + ":" + parts[1];
            byte[] expected = signature(unsigned, telegramUserId)
                    .getBytes(StandardCharsets.US_ASCII);
            byte[] actual = parts[2].getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            return Optional.of(new TrackingCallbackData(action, quoteSnapshotId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String signature(String unsigned, long telegramUserId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            byte[] digest = mac.doFinal(
                    (unsigned + ":" + telegramUserId).getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(Arrays.copyOf(digest, SIGNATURE_BYTES));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("tracking callback signature is unavailable", exception);
        }
    }

    private void validateIdentity(
            TrackingCallbackData.Action action,
            UUID quoteSnapshotId,
            long telegramUserId
    ) {
        if (action == null || quoteSnapshotId == null) {
            throw new IllegalArgumentException("tracking callback fields must not be null");
        }
        if (telegramUserId <= 0) {
            throw new IllegalArgumentException("telegramUserId must be positive");
        }
    }
}
