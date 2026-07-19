package com.priceradar.telegram.application;

public final class TelegramInlineButton {

    private final String text;
    private final String callbackData;

    public TelegramInlineButton(String text, String callbackData) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("button text must not be blank");
        }
        if (callbackData == null || callbackData.isBlank()) {
            throw new IllegalArgumentException("callbackData must not be blank");
        }
        if (callbackData.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64) {
            throw new IllegalArgumentException("callbackData must fit Telegram 64-byte limit");
        }
        this.text = text.trim();
        this.callbackData = callbackData.trim();
    }

    public String getText() {
        return text;
    }

    public String getCallbackData() {
        return callbackData;
    }
}
