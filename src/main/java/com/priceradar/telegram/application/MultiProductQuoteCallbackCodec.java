package com.priceradar.telegram.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public final class MultiProductQuoteCallbackCodec {

    public enum Action { ITEM, PAGE, ALL_MINIMUM }

    private static final String PREFIX = "MQ";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_BYTES = 6;
    private static final int CALLBACK_LIMIT_BYTES = 64;

    private final SecretKeySpec signingKey;

    public MultiProductQuoteCallbackCodec(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("multi-product callback secret must contain at least 32 bytes");
        }
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String encode(Action action, UUID sessionId, int value, long telegramUserId) {
        if (action == null || sessionId == null || value < 0 || telegramUserId <= 0) {
            throw new IllegalArgumentException("multi-product callback data is invalid");
        }
        String unsigned = PREFIX + ":" + action.name().charAt(0) + ":" + sessionId + ":" + value;
        String result = unsigned + ":" + signature(unsigned, telegramUserId);
        if (result.getBytes(StandardCharsets.UTF_8).length > CALLBACK_LIMIT_BYTES) {
            throw new IllegalStateException("multi-product callback exceeds Telegram limit");
        }
        return result;
    }

    public Optional<Decoded> decode(String data, long telegramUserId) {
        if (data == null || telegramUserId <= 0
                || data.getBytes(StandardCharsets.UTF_8).length > CALLBACK_LIMIT_BYTES) {
            return Optional.empty();
        }
        String[] parts = data.split(":", -1);
        if (parts.length != 5 || !PREFIX.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            Action action = switch (parts[1]) {
                case "I" -> Action.ITEM;
                case "P" -> Action.PAGE;
                case "A" -> Action.ALL_MINIMUM;
                default -> throw new IllegalArgumentException();
            };
            UUID sessionId = UUID.fromString(parts[2]);
            int value = Integer.parseInt(parts[3]);
            if (value < 0) return Optional.empty();
            String unsigned = String.join(":", parts[0], parts[1], parts[2], parts[3]);
            if (!MessageDigest.isEqual(
                    signature(unsigned, telegramUserId).getBytes(StandardCharsets.US_ASCII),
                    parts[4].getBytes(StandardCharsets.US_ASCII)
            )) {
                return Optional.empty();
            }
            return Optional.of(new Decoded(action, sessionId, value));
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
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Arrays.copyOf(digest, SIGNATURE_BYTES));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("multi-product callback signature is unavailable", exception);
        }
    }

    public static final class Decoded {
        private final Action action;
        private final UUID sessionId;
        private final int value;

        private Decoded(Action action, UUID sessionId, int value) {
            this.action = action;
            this.sessionId = sessionId;
            this.value = value;
        }

        public Action getAction() { return action; }
        public UUID getSessionId() { return sessionId; }
        public int getValue() { return value; }
    }
}
