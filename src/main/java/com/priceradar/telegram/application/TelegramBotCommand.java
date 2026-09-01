package com.priceradar.telegram.application;

import java.util.Locale;
import java.util.Optional;

public enum TelegramBotCommand {

    START("start", "🏠 Главное меню"),
    ADD("add", "➕ Добавить товар"),
    IMPORT("import", "🛒 Импортировать корзину"),
    TRACKED("tracked", "📦 Мои товары"),
    CITY("city", "🌍 Город"),
    HELP("help", "❓ Помощь"),
    FEEDBACK("feedback", "💬 Обратная связь");

    private final String command;
    private final String description;

    TelegramBotCommand(String command, String description) {
        this.command = command;
        this.description = description;
    }

    public String getCommand() {
        return command;
    }

    public String getDescription() {
        return description;
    }

    public static Optional<TelegramBotCommand> fromMessageText(String text) {
        Optional<String> commandName = commandName(text);
        if (commandName.isEmpty()) {
            return Optional.empty();
        }
        for (TelegramBotCommand command : values()) {
            if (command.command.equals(commandName.orElseThrow())) {
                return Optional.of(command);
            }
        }
        return Optional.empty();
    }

    public static boolean isCommandText(String text) {
        return commandName(text).isPresent();
    }

    private static Optional<String> commandName(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("/") || trimmed.length() == 1) {
            return Optional.empty();
        }
        int whitespace = firstWhitespace(trimmed);
        String token = whitespace < 0 ? trimmed : trimmed.substring(0, whitespace);
        int usernameSeparator = token.indexOf('@');
        if (usernameSeparator == token.length() - 1) {
            return Optional.empty();
        }
        String name = token.substring(1, usernameSeparator < 0 ? token.length() : usernameSeparator);
        if (name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(name.toLowerCase(Locale.ROOT));
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
