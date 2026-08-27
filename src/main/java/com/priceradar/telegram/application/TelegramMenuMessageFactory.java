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
                Отправьте ссылку Wildberries, текст из Copy/Share или список с несколькими ссылками.

                🛒 Импортировать корзину
                Можно добавить сразу несколько товаров из общей корзины Wildberries.

                📦 Мои товары
                Здесь находятся активные отслеживания и статистика.

                🌍 Город
                Выберите город, для которого бот будет получать цены и наличие.

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

                Например, просто вставьте ссылку:
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

                Одновременно можно отслеживать не более %d товаров. Если корзина больше, бот заранее предупредит об ограничении.

                Отправьте ссылку на общую корзину Wildberries.
                """.formatted(activeSubscriptionLimit);
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.home()
        );
    }
}
