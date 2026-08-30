package com.priceradar.telegram.application;

public final class TelegramMenuMessageFactory {

    private final int activeSubscriptionLimit;

    public TelegramMenuMessageFactory(int activeSubscriptionLimit) {
        if (activeSubscriptionLimit <= 0) {
            throw new IllegalArgumentException("active subscription limit must be positive");
        }
        this.activeSubscriptionLimit = activeSubscriptionLimit;
    }

    public OutgoingTelegramMessage welcome(long chatId) {
        String text = """
                Добро пожаловать в Price Radar!

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

                Price Radar помогает следить за ценами товаров Wildberries и уведомляет, когда появляется новая минимальная цена или цена снижается до выбранного значения.

                ➕ Добавить товар
                Отправьте одну или несколько ссылок на товары Wildberries.

                🛒 Импортировать корзину
                Добавьте сразу несколько товаров из общей корзины Wildberries.

                📦 Мои товары
                Здесь находятся активные отслеживания, настройки и статистика цен.

                🌍 Город
                Выберите город, для которого нужно показывать цены и наличие товаров.

                💬 Обратная связь
                Отправьте пожелание, идею или расскажите о найденной ошибке.

                Одновременно можно отслеживать до %d товаров.

                Для навигации используйте кнопки под сообщениями или меню команд Telegram.
                """.formatted(activeSubscriptionLimit);
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.home()
        );
    }

    public OutgoingTelegramMessage addProduct(long chatId) {
        String text = """
                ➕ Добавить товар

                🔗 Отправьте одну или несколько ссылок Wildberries в одном сообщении.

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

                После загрузки можно выбрать:

                ➕ Добавить новые
                Добавятся только товары, которых вы ещё не отслеживаете.

                🔄 Синхронизировать
                Список отслеживания будет приведён в соответствие с корзиной. Перед остановкой отслеживания товаров бот попросит подтверждение.

                Отправьте ссылку на корзину Wildberries.
                """;
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.home()
        );
    }
}
