package com.priceradar.telegram.application;

public final class TelegramMenuMessageFactory {

    private final boolean tutorialsEnabled;

    public TelegramMenuMessageFactory() {
        this(true);
    }

    public TelegramMenuMessageFactory(boolean tutorialsEnabled) {
        this.tutorialsEnabled = tutorialsEnabled;
    }

    public OutgoingTelegramMessage welcome(long chatId) {
        String text = """
                Добро пожаловать в Price Radar!

                Бот поможет следить за ценами на Wildberries:

                🔎 покажет текущую цену товара;
                🔔 сообщит о скидке;
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

    public OutgoingTelegramMessage help(long chatId, int activeSubscriptionLimit) {
        String text = """
                ❓ Помощь

                Price Radar следит за ценами товаров на Wildberries и сообщает, когда цена снижается.

                ➕ Как добавить товар
                Отправьте ссылку на товар Wildberries. Если у товара есть размеры, выберите нужный, а затем настройте уведомления.
                Можно выбрать один из двух вариантов:

                📉 Снижение цены — при каждом снижении будет приходить уведомление. Если цена повысилась, а потом опустилась до прежнего уровня — уведомление не придет.

                🎯 Целевая цена — придёт уведомление, когда цена товара станет ниже той, которую вы указали.

                📦 Сколько товаров можно отслеживать
                Одновременно вам доступно до %d товаров. Количество свободных мест можно посмотреть в разделах «Мои товары», «Добавить товар» и «Импорт корзины».

                🛒 Импорт корзины
                Можно отправить ссылку на корзину Wildberries и добавить сразу несколько товаров.

                Добавить новые — добавятся только те товары, которых ещё нет в отслеживании.

                Синхронизировать — список отслеживаемых товаров будет обновлён по вашей корзине. Товары, которых в ней нет, перестанут отслеживаться.

                💰 Цена и WB Кошелёк
                Для отслеживания, уведомлений и статистики используется обычная цена товара без WB Кошелька.
                Цена с WB Кошельком показывается отдельно для удобства. Размер вашей скидки WB Кошелька можно изменить в разделе «WB Кошелёк». По умолчанию размер скидки — 3%%.

                📊 История цены
                История цены товара начинается с момента, когда вы начали его отслеживать.

                ⏱️ Как часто проверяется цена
                Price Radar фиксирует цены раз в 6 часов.

                ❌ Если товар закончился
                Отслеживание не останавливается. Price Radar продолжит проверять товар и снова начнёт учитывать цену, когда он появится в наличии.

                🌍 Выбор города
                Цена и наличие товара зависят от региона. Пока у вас есть товары в режиме отслеживания, изменить город нельзя. Для смены города необходимо сначала остановить все отслеживания.

                💬 Обратная связь
                Есть вопрос, пожелание или вы нашли ошибку? Напишите нам через раздел «Обратная связь».
                """.formatted(activeSubscriptionLimit);
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.home()
        );
    }

    public OutgoingTelegramMessage addProduct(
            long chatId,
            int activeSubscriptions,
            int activeSubscriptionLimit
    ) {
        String text = """
                ➕ Добавить товар

                🔗 Чтобы начать отслеживание, отправьте одну или несколько ссылок Wildberries в одном сообщении.

                Например:
                https://www.wildberries.ru/catalog/10302970123/detail.aspx
                """;
        text += "\n" + TelegramDisplayFormatter.trackingCapacity(
                activeSubscriptions, activeSubscriptionLimit
        );
        return new OutgoingTelegramMessage(
                chatId,
                text,
                tutorialKeyboard(TutorialTopic.PRODUCT)
        );
    }

    public OutgoingTelegramMessage unknownCommand(long chatId) {
        return new OutgoingTelegramMessage(
                chatId,
                "Неизвестная команда.\nИспользуйте меню Telegram или /help.",
                TelegramNavigationKeyboard.home()
        );
    }

    public OutgoingTelegramMessage importBasket(
            long chatId,
            int activeSubscriptions,
            int activeSubscriptionLimit
    ) {
        String text = """
                🛒 Импорт корзины Wildberries

                В Wildberries откройте корзину, выберите «Поделиться корзиной» и отправьте полученную ссылку сюда.

                После загрузки можно выбрать:

                ➕ Добавить новые
                Добавятся только те товары, которые вы ещё не отслеживаете.

                🔄 Синхронизировать
                Список отслеживаемых товаров будет совпадать с вашей корзиной.

                Отправьте ссылку на корзину Wildberries:
                """;
        text += "\n" + TelegramDisplayFormatter.trackingCapacity(
                activeSubscriptions, activeSubscriptionLimit
        );
        return new OutgoingTelegramMessage(
                chatId,
                text,
                tutorialKeyboard(TutorialTopic.BASKET)
        );
    }

    private java.util.List<java.util.List<TelegramInlineButton>> tutorialKeyboard(
            TutorialTopic topic
    ) {
        if (!tutorialsEnabled) {
            return TelegramNavigationKeyboard.home();
        }
        return java.util.List.of(
                java.util.List.of(new TelegramInlineButton(
                        "📖 Инструкция", TutorialCallbackData.open(topic)
                )),
                java.util.List.of(TelegramNavigationKeyboard.button(
                        MainMenuCallbackData.Action.HOME
                ))
        );
    }
}
