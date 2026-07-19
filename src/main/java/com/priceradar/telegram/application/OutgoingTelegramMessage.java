package com.priceradar.telegram.application;

import java.util.ArrayList;
import java.util.List;

public final class OutgoingTelegramMessage {

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
        if (inlineKeyboard == null) {
            throw new IllegalArgumentException("inlineKeyboard must not be null");
        }
        this.chatId = chatId;
        this.text = text.trim();
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
