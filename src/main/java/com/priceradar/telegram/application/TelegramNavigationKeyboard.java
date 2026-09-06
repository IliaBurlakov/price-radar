package com.priceradar.telegram.application;

import java.util.List;
import java.util.UUID;

public final class TelegramNavigationKeyboard {

    private TelegramNavigationKeyboard() {
    }

    public static List<List<TelegramInlineButton>> mainMenu() {
        return List.of(
                List.of(button(MainMenuCallbackData.Action.ADD_PRODUCT)),
                List.of(button(MainMenuCallbackData.Action.IMPORT_BASKET)),
                List.of(
                        button(MainMenuCallbackData.Action.TRACKED_ITEMS),
                        button(MainMenuCallbackData.Action.REGION)
                ),
                List.of(button(MainMenuCallbackData.Action.WALLET_DISCOUNT)),
                List.of(
                        button(MainMenuCallbackData.Action.HELP),
                        button(MainMenuCallbackData.Action.FEEDBACK)
                )
        );
    }

    public static List<List<TelegramInlineButton>> home() {
        return List.of(List.of(button(MainMenuCallbackData.Action.HOME)));
    }

    public static List<List<TelegramInlineButton>> addProductAndHome() {
        return List.of(List.of(
                button(MainMenuCallbackData.Action.ADD_PRODUCT),
                button(MainMenuCallbackData.Action.HOME)
        ));
    }

    public static List<List<TelegramInlineButton>> trackedItemsAndHome() {
        return List.of(List.of(
                button(MainMenuCallbackData.Action.TRACKED_ITEMS),
                button(MainMenuCallbackData.Action.HOME)
        ));
    }

    public static List<List<TelegramInlineButton>> itemSubscreen(UUID subscriptionId) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId must not be null");
        }
        return List.of(
                List.of(new TelegramInlineButton(
                        "← Назад",
                        SubscriptionCallbackData.encode(
                                SubscriptionCallbackData.Action.OPEN_ITEM,
                                subscriptionId
                        )
                )),
                trackedItemsAndHome().getFirst()
        );
    }

    public static TelegramInlineButton button(
            String text,
            MainMenuCallbackData.Action action
    ) {
        return new TelegramInlineButton(text, MainMenuCallbackData.encode(action));
    }

    public static TelegramInlineButton button(MainMenuCallbackData.Action action) {
        return button(label(action), action);
    }

    private static String label(MainMenuCallbackData.Action action) {
        return switch (action) {
            case HOME -> "🏠 Главное меню";
            case ADD_PRODUCT -> "➕ Добавить товар";
            case IMPORT_BASKET -> "🛒 Импортировать корзину";
            case TRACKED_ITEMS -> "📦 Мои товары";
            case REGION -> "🌍 Город";
            case WALLET_DISCOUNT -> "💳 WB Кошелёк";
            case HELP -> "❓ Помощь";
            case FEEDBACK -> "💬 Обратная связь";
        };
    }
}
