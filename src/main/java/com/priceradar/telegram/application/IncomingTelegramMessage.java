package com.priceradar.telegram.application;

public final class IncomingTelegramMessage {

    private final long telegramUserId;
    private final long chatId;
    private final String chatType;
    private final String text;

    public IncomingTelegramMessage(
            long telegramUserId,
            long chatId,
            String chatType,
            String text
    ) {
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        if (chatType == null || chatType.isBlank()) {
            throw new IllegalArgumentException("chatType must not be blank");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.chatType = chatType.trim();
        this.text = text.trim();
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public long getChatId() {
        return chatId;
    }

    public String getChatType() {
        return chatType;
    }

    public String getText() {
        return text;
    }

    public boolean isPrivateChat() {
        return "private".equals(chatType);
    }
}
