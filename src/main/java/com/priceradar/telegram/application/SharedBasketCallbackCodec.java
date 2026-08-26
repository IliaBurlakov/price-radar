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

public final class SharedBasketCallbackCodec {

    public enum Action {
        ADD("A"), ADD_CONFIRM("AC"), SYNC("S"), SYNC_CONFIRM("SC"), BACK("B"), CANCEL("C");
        private final String code;
        Action(String code) { this.code = code; }
        private static Optional<Action> fromCode(String code) {
            return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst();
        }
    }

    public static final class Decoded {
        private final Action action;
        private final UUID importId;
        private final Optional<String> planFingerprint;
        private Decoded(Action action, UUID importId, Optional<String> planFingerprint) {
            this.action = action;
            this.importId = importId;
            this.planFingerprint = planFingerprint;
        }
        public Action getAction() { return action; }
        public UUID getImportId() { return importId; }
        public Optional<String> getPlanFingerprint() { return planFingerprint; }
    }

    private static final String PREFIX = "BI";
    private static final String HMAC = "HmacSHA256";
    private static final int SIGNATURE_BYTES = 6;
    private final SecretKeySpec signingKey;

    public SharedBasketCallbackCodec(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("shared basket callback secret must contain at least 32 bytes");
        }
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC);
    }

    public String encode(Action action, UUID importId, long telegramUserId) {
        if (action == null || importId == null || telegramUserId <= 0) throw new IllegalArgumentException("callback identity is invalid");
        if (action == Action.SYNC_CONFIRM) {
            throw new IllegalArgumentException("sync confirmation requires a destructive plan fingerprint");
        }
        String unsigned = PREFIX + ":" + action.code + ":" + importId;
        return unsigned + ":" + sign(unsigned, telegramUserId);
    }

    public String encodeSyncConfirm(UUID importId, String planFingerprint, long telegramUserId) {
        if (importId == null || telegramUserId <= 0 || planFingerprint == null
                || !planFingerprint.matches("[A-Za-z0-9_-]{11}")) {
            throw new IllegalArgumentException("sync confirmation identity is invalid");
        }
        String unsigned = PREFIX + ":" + Action.SYNC_CONFIRM.code + ":" + importId + ":" + planFingerprint;
        return unsigned + ":" + sign(unsigned, telegramUserId);
    }

    public Optional<Decoded> decode(String data, long telegramUserId) {
        if (data == null || telegramUserId <= 0 || data.getBytes(StandardCharsets.UTF_8).length > 64) return Optional.empty();
        String[] parts = data.split(":", -1);
        if ((parts.length != 4 && parts.length != 5) || !PREFIX.equals(parts[0])) return Optional.empty();
        try {
            Optional<Action> action = Action.fromCode(parts[1]);
            UUID importId = UUID.fromString(parts[2]);
            boolean syncConfirmation = action.filter(value -> value == Action.SYNC_CONFIRM).isPresent();
            if (syncConfirmation != (parts.length == 5)
                    || (syncConfirmation && !parts[3].matches("[A-Za-z0-9_-]{11}"))) return Optional.empty();
            String unsigned = String.join(":", Arrays.copyOf(parts, parts.length - 1));
            if (action.isEmpty() || !MessageDigest.isEqual(
                    sign(unsigned, telegramUserId).getBytes(StandardCharsets.US_ASCII),
                    parts[parts.length - 1].getBytes(StandardCharsets.US_ASCII))) return Optional.empty();
            return Optional.of(new Decoded(
                    action.orElseThrow(), importId,
                    syncConfirmation ? Optional.of(parts[3]) : Optional.empty()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String sign(String value, long telegramUserId) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(signingKey);
            byte[] digest = mac.doFinal((value + ":" + telegramUserId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, SIGNATURE_BYTES));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("shared basket callback signing is unavailable", exception);
        }
    }
}
