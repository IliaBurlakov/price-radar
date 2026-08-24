package com.priceradar.telegram.application;

public final class TelegramMenuMessageFactory {

    public OutgoingTelegramMessage welcome(long chatId) {
        String text = """
                Добро пожаловать в PriceRadar!

                Бот поможет следить за ценами на Wildberries:
                🔎 покажет текущую цену товара;
                🔔 сообщит о новой минимальной цене;
                🎯 уведомит, когда цена достигнет выбранного значения;
                📊 покажет историю изменения цены.

                Чтобы начать, отправьте ссылку на товар или выберите нужный раздел.
                """;
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.mainMenu()
        );
    }

    public OutgoingTelegramMessage mainMenu(long chatId) {
        return new OutgoingTelegramMessage(
                chatId,
                "Что вы хотите сделать?",
                TelegramNavigationKeyboard.mainMenu()
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
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.home()
        );
    }

    public OutgoingTelegramMessage addProduct(long chatId) {
        String text = """
                Отправьте ссылку на товар Wildberries.

                Например:
                https://www.wildberries.ru/catalog/10302970/detail.aspx
                """;
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.home()
        );
    }
}
