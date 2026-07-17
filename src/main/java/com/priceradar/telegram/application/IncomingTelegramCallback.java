package com.priceradar.telegram.application;

public final class IncomingTelegramCallback {

    private final String callbackQueryId;
    private final long telegramUserId;
    private final long chatId;
    private final String chatType;
    private final String data;

    public IncomingTelegramCallback(
            String callbackQueryId,
            long telegramUserId,
            long chatId,
            String chatType,
            String data
    ) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            throw new IllegalArgumentException("callbackQueryId must not be blank");
        }
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        if (chatType == null || chatType.isBlank()) {
            throw new IllegalArgumentException("chatType must not be blank");
        }
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("callback data must not be blank");
        }
        this.callbackQueryId = callbackQueryId.trim();
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.chatType = chatType.trim();
        this.data = data.trim();
    }

    public String getCallbackQueryId() {
        return callbackQueryId;
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public long getChatId() {
        return chatId;
    }

    public String getData() {
        return data;
    }

    public boolean isPrivateChat() {
        return "private".equals(chatType);
    }
}
