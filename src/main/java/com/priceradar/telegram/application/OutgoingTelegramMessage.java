package com.priceradar.telegram.application;

import java.util.ArrayList;
import java.util.List;

public final class OutgoingTelegramMessage {

    private static final int MAX_TEXT_CODE_POINTS = 4096;

    private final long chatId;
    private final String text;
    private final List<List<TelegramInlineButton>> inlineKeyboard;

    public OutgoingTelegramMessage(
            long chatId,
            String text,
            List<List<TelegramInlineButton>> inlineKeyboard
    ) {
        if (chatId <= 0) {
            throw new IllegalArgumentException("chatId must be positive");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("message text must not be blank");
        }
        String normalizedText = text.trim();
        if (normalizedText.codePointCount(0, normalizedText.length()) > MAX_TEXT_CODE_POINTS) {
            throw new IllegalArgumentException("message text must fit Telegram 4096-character limit");
        }
        if (inlineKeyboard == null) {
            throw new IllegalArgumentException("inlineKeyboard must not be null");
        }
        this.chatId = chatId;
        this.text = normalizedText;
        this.inlineKeyboard = copyKeyboard(inlineKeyboard);
    }

    public static OutgoingTelegramMessage text(long chatId, String text) {
        return new OutgoingTelegramMessage(chatId, text, List.of());
    }

    public long getChatId() {
        return chatId;
    }

    public String getText() {
        return text;
    }

    public List<List<TelegramInlineButton>> getInlineKeyboard() {
        return inlineKeyboard;
    }

    private List<List<TelegramInlineButton>> copyKeyboard(
            List<List<TelegramInlineButton>> keyboard
    ) {
        List<List<TelegramInlineButton>> copy = new ArrayList<>();
        for (List<TelegramInlineButton> row : keyboard) {
            if (row == null || row.isEmpty() || row.stream().anyMatch(button -> button == null)) {
                throw new IllegalArgumentException("keyboard rows must contain buttons");
            }
            copy.add(List.copyOf(row));
        }
        return List.copyOf(copy);
    }
}
