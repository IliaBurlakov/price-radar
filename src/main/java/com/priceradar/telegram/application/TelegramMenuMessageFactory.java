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
                ❓ Помощь

                PriceRadar следит за ценами товаров Wildberries и уведомляет о новых минимальных или желаемых ценах.

                ➕ Добавить товар
                Отправьте ссылку на один товар Wildberries.

                🛒 Импортировать корзину
                Можно добавить сразу несколько товаров из общей корзины Wildberries.

                📦 Мои товары
                Здесь находятся активные отслеживания и статистика.

                Одновременно можно отслеживать до 50 товаров.

                Для навигации используйте кнопки под сообщениями или меню команд Telegram.
                """;
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.home()
        );
    }

    public OutgoingTelegramMessage addProduct(long chatId) {
        String text = """
                ➕ Добавить товар

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

    public OutgoingTelegramMessage unknownCommand(long chatId) {
        return new OutgoingTelegramMessage(
                chatId,
                "Неизвестная команда.\nИспользуйте меню Telegram или /help.",
                TelegramNavigationKeyboard.home()
        );
    }

    public OutgoingTelegramMessage importBasket(long chatId) {
        String text = """
                🛒 Импорт корзины Wildberries

                В Wildberries откройте корзину, выберите «Поделиться корзиной» и отправьте полученную ссылку сюда.

                После загрузки можно:

                ➕ Добавить новые
                Бот добавит товары, которых вы ещё не отслеживаете. Остальные останутся без изменений.

                🔄 Синхронизировать
                Новые товары добавятся, а товары, которых нет в корзине, будут удалены из отслеживания.

                ⚠️ Перед удалением бот обязательно попросит подтверждение.

                Одновременно можно отслеживать не более 50 товаров. Если корзина больше, бот заранее предупредит об ограничении.

                Отправьте ссылку на общую корзину Wildberries.
                """;
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.home()
        );
    }
}
