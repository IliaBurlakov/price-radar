package com.priceradar.telegram.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

public final class ClearTrackingCallbackCodec {

    private static final String PREFIX = "CLEAR_ALL_CONFIRM:";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_BYTES = 6;
    private final SecretKeySpec signingKey;

    public ClearTrackingCallbackCodec(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("clear tracking callback secret must contain at least 32 bytes");
        }
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String encodeConfirm(String fingerprint, long telegramUserId) {
        validate(fingerprint, telegramUserId);
        String unsigned = PREFIX + fingerprint;
        return unsigned + ":" + signature(unsigned, telegramUserId);
    }

    public Optional<String> decodeConfirm(String data, long telegramUserId) {
        if (data == null || telegramUserId <= 0 || !data.startsWith(PREFIX)) return Optional.empty();
        String[] parts = data.substring(PREFIX.length()).split(":", -1);
        if (parts.length != 2 || parts[0].isBlank()) return Optional.empty();
        String unsigned = PREFIX + parts[0];
        if (!MessageDigest.isEqual(
                signature(unsigned, telegramUserId).getBytes(StandardCharsets.US_ASCII),
                parts[1].getBytes(StandardCharsets.US_ASCII)
        )) return Optional.empty();
        return Optional.of(parts[0]);
    }

    private String signature(String value, long telegramUserId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            byte[] digest = mac.doFinal((value + ":" + telegramUserId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Arrays.copyOf(digest, SIGNATURE_BYTES));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("clear tracking callback signature is unavailable", exception);
        }
    }

    private void validate(String fingerprint, long telegramUserId) {
        if (fingerprint == null || fingerprint.isBlank() || telegramUserId <= 0) {
            throw new IllegalArgumentException("clear tracking callback fields are invalid");
        }
    }
}
