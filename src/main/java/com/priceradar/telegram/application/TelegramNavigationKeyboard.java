package com.priceradar.telegram.application;

import java.util.List;

public final class TelegramNavigationKeyboard {

    private TelegramNavigationKeyboard() {
    }

    public static List<List<TelegramInlineButton>> mainMenu() {
        return List.of(
                List.of(button("Добавить товар", MainMenuCallbackData.Action.ADD_PRODUCT)),
                List.of(
                        button("Мои товары", MainMenuCallbackData.Action.TRACKED_ITEMS),
                        button("Помощь", MainMenuCallbackData.Action.HELP)
                )
        );
    }

    public static List<List<TelegramInlineButton>> home() {
        return List.of(List.of(
                button("Главное меню", MainMenuCallbackData.Action.HOME)
        ));
    }

    public static List<List<TelegramInlineButton>> addProductAndHome() {
        return List.of(List.of(
                button("Добавить товар", MainMenuCallbackData.Action.ADD_PRODUCT),
                button("Главное меню", MainMenuCallbackData.Action.HOME)
        ));
    }

    public static List<List<TelegramInlineButton>> trackedItemsAndHome() {
        return List.of(List.of(
                button("Мои товары", MainMenuCallbackData.Action.TRACKED_ITEMS),
                button("Главное меню", MainMenuCallbackData.Action.HOME)
        ));
    }

    public static TelegramInlineButton button(
            String text,
            MainMenuCallbackData.Action action
    ) {
        return new TelegramInlineButton(text, MainMenuCallbackData.encode(action));
    }
}
