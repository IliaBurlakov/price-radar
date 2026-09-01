package com.priceradar.telegram.application;

import java.util.List;

public final class TelegramTutorialMessageFactory {

    public OutgoingTelegramMessage platformChooser(long chatId, TutorialTopic topic) {
        String title = topic == TutorialTopic.PRODUCT
                ? "📖 Как добавить товар"
                : "📖 Как импортировать корзину";
        MainMenuCallbackData.Action backAction = topic == TutorialTopic.PRODUCT
                ? MainMenuCallbackData.Action.ADD_PRODUCT
                : MainMenuCallbackData.Action.IMPORT_BASKET;
        return new OutgoingTelegramMessage(
                chatId,
                title + "\n\nВыберите устройство:",
                List.of(
                        List.of(new TelegramInlineButton(
                                "🍎 iPhone", TutorialCallbackData.show(topic, TutorialPlatform.IPHONE)
                        )),
                        List.of(new TelegramInlineButton(
                                "🤖 Android", TutorialCallbackData.show(topic, TutorialPlatform.ANDROID)
                        )),
                        List.of(new TelegramInlineButton(
                                "💻 Компьютер (Windows)",
                                TutorialCallbackData.show(topic, TutorialPlatform.WINDOWS)
                        )),
                        List.of(new TelegramInlineButton(
                                "← Назад", MainMenuCallbackData.encode(backAction)
                        )),
                        List.of(new TelegramInlineButton(
                                "Главное меню",
                                MainMenuCallbackData.encode(MainMenuCallbackData.Action.HOME)
                        ))
                )
        );
    }

    public OutgoingTelegramMessage navigation(long chatId, TutorialTopic topic) {
        return new OutgoingTelegramMessage(
                chatId,
                "Отправьте скопированную ссылку:",
                List.of(
                        List.of(new TelegramInlineButton(
                                "← Назад", TutorialCallbackData.open(topic)
                        ), new TelegramInlineButton(
                                "Главное меню",
                                MainMenuCallbackData.encode(MainMenuCallbackData.Action.HOME)
                        ))
                )
        );
    }

    public OutgoingTelegramMessage loading(long chatId) {
        return OutgoingTelegramMessage.text(chatId, "⏳ Загружаю инструкцию...");
    }

    public String instructionText(
            TutorialTopic topic,
            TutorialPlatform platform
    ) {
        return switch (topic) {
            case PRODUCT -> productText(platform);
            case BASKET -> basketText(platform);
        };
    }

    private String productText(TutorialPlatform platform) {
        return switch (platform) {
            case IPHONE -> """
                    📖 Как добавить товар с iPhone

                    1. Откройте нужный товар в Wildberries и нажмите кнопку «Поделиться» в правом верхнем углу.
                    2. В открывшемся меню нажмите «Скопировать».
                    3. Вернитесь в Price Radar, вставьте скопированную ссылку в чат и отправьте её. После проверки выберите, как отслеживать цену.
                    """;
            case ANDROID -> """
                    📖 Как добавить товар с Android

                    1. Откройте нужный товар в Wildberries и нажмите кнопку «Поделиться» в правом верхнем углу.
                    2. В открывшемся меню нажмите кнопку копирования ссылки.
                    3. Вернитесь в Price Radar, вставьте скопированную ссылку в чат и отправьте её. После проверки выберите, как отслеживать цену.
                    """;
            case WINDOWS -> """
                    📖 Как добавить товар на компьютере

                    1. Откройте нужный товар Wildberries в браузере и нажмите на адресную строку сверху.
                    2. Скопируйте ссылку: нажмите правой кнопкой мыши и выберите «Копировать». Можно также использовать Ctrl+C.
                    3. Откройте Price Radar в Telegram, вставьте ссылку в чат и отправьте её. После проверки выберите, как отслеживать цену.
                    """;
        };
    }

    private String basketText(TutorialPlatform platform) {
        return switch (platform) {
            case IPHONE -> """
                    📖 Как импортировать корзину с iPhone

                    1. Откройте корзину Wildberries и нажмите кнопку «Поделиться».
                    2. Нажмите «Скопировать ссылку».
                    3. Вернитесь в Price Radar, откройте «Импортировать корзину», вставьте ссылку и отправьте её. Затем выберите «Добавить новые» или «Синхронизировать».

                    Если вы не уверены, какой вариант выбрать, используйте «Добавить новые» — бот просто добавит товары, которых вы ещё не отслеживаете.
                    """;
            case ANDROID -> """
                    📖 Как импортировать корзину с Android

                    1. Откройте корзину Wildberries и нажмите кнопку «Поделиться».
                    2. Нажмите «Скопировать ссылку».
                    3. Вернитесь в Price Radar, вставьте скопированную ссылку в чат и отправьте её. Затем выберите «Добавить новые» или «Синхронизировать».

                    Если вы не уверены, какой вариант выбрать, используйте «Добавить новые» — бот просто добавит товары, которых вы ещё не отслеживаете.
                    """;
            case WINDOWS -> """
                    📖 Как импортировать корзину на компьютере

                    1. Откройте корзину Wildberries и нажмите кнопку «Поделиться».
                    2. Нажмите «Скопировать ссылку на товары».
                    3. Откройте в Price Radar раздел «Импортировать корзину», вставьте ссылку и отправьте её. Затем выберите «Добавить новые» или «Синхронизировать».

                    Если вы не уверены, какой вариант выбрать, используйте «Добавить новые» — бот просто добавит товары, которых вы ещё не отслеживаете.
                    """;
        };
    }
}
