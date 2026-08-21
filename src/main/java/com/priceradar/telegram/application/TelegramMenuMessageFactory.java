package com.priceradar.telegram.application;

import java.util.List;

public final class TelegramMenuMessageFactory {

    public OutgoingTelegramMessage welcome(long chatId) {
        String text = """
                Добро пожаловать в PriceRadar!

                Бот поможет следить за ценами на Wildberries:
                • покажет текущую цену товара;
                • сообщит о снижении;
                • уведомит, когда цена достигнет выбранного значения;
                • покажет историю изменения цены.

                Чтобы начать, отправьте ссылку на товар или выберите нужный раздел.
                """;
        return new OutgoingTelegramMessage(chatId, text, mainKeyboard());
    }

    public OutgoingTelegramMessage mainMenu(long chatId) {
        return new OutgoingTelegramMessage(
                chatId,
                "Что вы хотите сделать?",
                mainKeyboard()
        );
    }

    public OutgoingTelegramMessage help(long chatId) {
        String text = """
                Как пользоваться PriceRadar

                1. Отправьте ссылку на товар Wildberries.
                2. Посмотрите текущую цену и выберите способ отслеживания.
                3. Бот проверит цену автоматически и пришлёт уведомление.

                Раздел «Мои товары» показывает активные подписки, последнюю цену и статистику.

                Цена может отличаться от итоговой цены в приложении Wildberries.
                """;
        return new OutgoingTelegramMessage(chatId, text, homeKeyboard());
    }

    public OutgoingTelegramMessage addProduct(long chatId) {
        String text = """
                Отправьте ссылку на товар Wildberries.

                Например:
                https://www.wildberries.ru/catalog/10302970/detail.aspx
                """;
        return new OutgoingTelegramMessage(chatId, text, homeKeyboard());
    }

    private List<List<TelegramInlineButton>> mainKeyboard() {
        return List.of(
                List.of(button("Добавить товар", MainMenuCallbackData.Action.ADD_PRODUCT)),
                List.of(
                        button("Мои товары", MainMenuCallbackData.Action.TRACKED_ITEMS),
                        button("Помощь", MainMenuCallbackData.Action.HELP)
                )
        );
    }

    private List<List<TelegramInlineButton>> homeKeyboard() {
        return List.of(List.of(button("Главное меню", MainMenuCallbackData.Action.HOME)));
    }

    private TelegramInlineButton button(String text, MainMenuCallbackData.Action action) {
        return new TelegramInlineButton(text, MainMenuCallbackData.encode(action));
    }
}
