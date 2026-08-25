package com.priceradar.telegram.application;

import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.application.WalletEstimate;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.user.domain.UserPricePreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TrackedItemsMessageFactory {

    public static final int ITEMS_PER_PAGE = 8;

    private static final int MAX_BUTTON_TITLE_LENGTH = 48;
    private static final int MAX_DETAIL_LENGTH = 60;
    private final WalletEstimateService walletEstimateService;

    public TrackedItemsMessageFactory(WalletEstimateService walletEstimateService) {
        if (walletEstimateService == null) {
            throw new IllegalArgumentException("walletEstimateService must not be null");
        }
        this.walletEstimateService = walletEstimateService;
    }

    public OutgoingTelegramMessage createList(
            long chatId,
            List<TrackedSubscriptionItem> items,
            int pageNumber
    ) {
        validateItems(items);
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        if (items.isEmpty()) {
            return emptyList(chatId);
        }

        int pageCount = pageCount(items.size());
        if (pageNumber >= pageCount) {
            throw new IllegalArgumentException("pageNumber is outside available pages");
        }

        int from = pageNumber * ITEMS_PER_PAGE;
        int to = Math.min(from + ITEMS_PER_PAGE, items.size());
        StringBuilder text = new StringBuilder("Мои товары: ")
                .append(items.size());
        if (pageCount > 1) {
            text.append("\nСтраница ")
                    .append(pageNumber + 1)
                    .append(" из ")
                    .append(pageCount);
        }
        text.append("\n\nВыберите товар:");

        List<List<TelegramInlineButton>> keyboard = new ArrayList<>();
        for (int index = from; index < to; index++) {
            TrackedSubscriptionItem item = items.get(index);
            keyboard.add(List.of(new TelegramInlineButton(
                    itemButtonText(item, index + 1),
                    SubscriptionCallbackData.encode(
                            SubscriptionCallbackData.Action.OPEN_ITEM,
                            item.getSubscriptionId()
                    )
            )));
        }
        appendPagination(keyboard, pageNumber, pageCount);
        keyboard.add(List.of(new TelegramInlineButton(
                "🧹 Очистить все",
                ClearTrackingCallbackData.START
        )));
        keyboard.add(List.of(
                new TelegramInlineButton(
                        "Добавить товар",
                        MainMenuCallbackData.encode(MainMenuCallbackData.Action.ADD_PRODUCT)
                ),
                new TelegramInlineButton(
                        "Главное меню",
                        MainMenuCallbackData.encode(MainMenuCallbackData.Action.HOME)
                )
        ));
        return new OutgoingTelegramMessage(chatId, text.toString(), keyboard);
    }

    public OutgoingTelegramMessage createDetails(
            long chatId,
            TrackedSubscriptionItem item,
            int displayNumber,
            int listPage,
            String region,
            UserPricePreferences preferences
    ) {
        if (item == null || preferences == null || region == null || region.isBlank()) {
            throw new IllegalArgumentException("tracked item details must not be null or blank");
        }
        if (displayNumber <= 0 || listPage < 0) {
            throw new IllegalArgumentException("tracked item position must be valid");
        }

        StringBuilder text = new StringBuilder()
                .append(displayNumber)
                .append(". ")
                .append(item.getTitle().orElse("Товар Wildberries #" + item.getNmId()));
        item.getBrand().ifPresent(brand -> text.append("\nБренд: ")
                .append(truncate(brand, MAX_DETAIL_LENGTH)));
        item.getVariantDisplayName()
                .flatMap(TelegramDisplayFormatter::variant)
                .ifPresent(variant -> text.append("\n")
                        .append(truncate(variant, MAX_DETAIL_LENGTH)));
        text.append("\n\nРежим: ").append(mode(item));
        appendLatestPrice(text, item, preferences);
        item.getLatestObservedAt().ifPresent(observedAt -> text.append("\nПроверено: ")
                .append(TelegramDisplayFormatter.observedAt(observedAt)));
        text.append("\n\nРегион: ").append(TelegramDisplayFormatter.region(region));
        text.append("\nОткрыть товар:\n").append(item.getCanonicalUrl());
        text.append("\n\n").append(TelegramDisplayFormatter.approximatePriceWarning());

        List<List<TelegramInlineButton>> keyboard = List.of(
                List.of(
                        new TelegramInlineButton(
                                "💰 Последняя цена",
                                SubscriptionCallbackData.encode(
                                        SubscriptionCallbackData.Action.SHOW_LAST_KNOWN,
                                        item.getSubscriptionId()
                                )
                        ),
                        new TelegramInlineButton(
                                "📊 Статистика",
                                SubscriptionCallbackData.encode(
                                        SubscriptionCallbackData.Action.SHOW_STATISTICS,
                                        item.getSubscriptionId()
                                )
                        )
                ),
                List.of(new TelegramInlineButton(
                        "❌ Удалить товар",
                        SubscriptionCallbackData.encode(
                                SubscriptionCallbackData.Action.REMOVE,
                                item.getSubscriptionId()
                        )
                )),
                List.of(
                        new TelegramInlineButton(
                                "← Назад",
                                TrackedItemsPageCallbackData.encode(listPage)
                        ),
                        TelegramNavigationKeyboard.button(
                                "Главное меню",
                                MainMenuCallbackData.Action.HOME
                        )
                )
        );
        return new OutgoingTelegramMessage(chatId, text.toString(), keyboard);
    }

    public OutgoingTelegramMessage createRemovalConfirmation(
            long chatId,
            TrackedSubscriptionItem item
    ) {
        if (item == null) {
            throw new IllegalArgumentException("tracked item must not be null");
        }
        String title = item.getTitle().orElse("Товар Wildberries #" + item.getNmId());
        return new OutgoingTelegramMessage(
                chatId,
                "⚠️ Прекратить отслеживание?\n\n" + truncate(title, MAX_DETAIL_LENGTH)
                        + "\n\nИстория текущего периода отслеживания будет завершена.",
                List.of(
                        List.of(new TelegramInlineButton(
                                "Да, удалить",
                                SubscriptionCallbackData.encode(
                                        SubscriptionCallbackData.Action.CONFIRM_REMOVE,
                                        item.getSubscriptionId()
                                )
                        )),
                        List.of(new TelegramInlineButton(
                                "← Назад",
                                SubscriptionCallbackData.encode(
                                        SubscriptionCallbackData.Action.OPEN_ITEM,
                                        item.getSubscriptionId()
                                )
                        )),
                        TelegramNavigationKeyboard.trackedItemsAndHome().getFirst()
                )
        );
    }

    private OutgoingTelegramMessage emptyList(long chatId) {
        return new OutgoingTelegramMessage(
                chatId,
                "У вас пока нет отслеживаемых товаров.\n\n"
                        + "Отправьте ссылку Wildberries, чтобы добавить первый товар.",
                List.of(List.of(
                        TelegramNavigationKeyboard.button(
                                "Добавить товар",
                                MainMenuCallbackData.Action.ADD_PRODUCT
                        ),
                        TelegramNavigationKeyboard.button(
                                "Главное меню",
                                MainMenuCallbackData.Action.HOME
                        )
                ))
        );
    }

    private void appendPagination(
            List<List<TelegramInlineButton>> keyboard,
            int pageNumber,
            int pageCount
    ) {
        if (pageCount <= 1) {
            return;
        }
        List<TelegramInlineButton> row = new ArrayList<>(2);
        if (pageNumber > 0) {
            row.add(new TelegramInlineButton(
                    "← Назад",
                    TrackedItemsPageCallbackData.encode(pageNumber - 1)
            ));
        }
        if (pageNumber + 1 < pageCount) {
            row.add(new TelegramInlineButton(
                    "Дальше →",
                    TrackedItemsPageCallbackData.encode(pageNumber + 1)
            ));
        }
        keyboard.add(List.copyOf(row));
    }

    private void appendLatestPrice(
            StringBuilder text,
            TrackedSubscriptionItem item,
            UserPricePreferences preferences
    ) {
        if (item.getLatestSnapshotStatus().isEmpty()) {
            text.append("\nПоследняя цена: ещё не проверялась");
            return;
        }

        SnapshotStatus status = item.getLatestSnapshotStatus().orElseThrow();
        if (status == SnapshotStatus.REGULAR_PRICE) {
            RubleAmount regularPrice = item.getLatestRegularPrice().orElseThrow();
            text.append("\nЦена без WB Кошелька: ").append(format(regularPrice));
            estimateWalletPrice(regularPrice, preferences)
                    .ifPresent(estimate -> text.append("\nС WB Кошельком: ")
                            .append(TelegramDisplayFormatter.walletEstimate(estimate)));
            return;
        }
        if (status == SnapshotStatus.BASIC_FALLBACK) {
            text.append("\nПоследняя цена: точная цена пока недоступна");
            return;
        }
        if (status == SnapshotStatus.UNAVAILABLE) {
            text.append("\nПоследняя цена: товар недоступен");
            return;
        }
        text.append("\nПоследняя цена: цена не найдена");
    }

    private Optional<WalletEstimate> estimateWalletPrice(
            RubleAmount regularPrice,
            UserPricePreferences preferences
    ) {
        return walletEstimateService.estimateFromRegularPrice(regularPrice, preferences);
    }

    private String itemButtonText(TrackedSubscriptionItem item, int displayNumber) {
        String title = item.getTitle().orElse("Товар Wildberries #" + item.getNmId());
        return displayNumber + ". " + truncate(title, MAX_BUTTON_TITLE_LENGTH);
    }

    private String mode(TrackedSubscriptionItem item) {
        if (item.getNotificationMode() == NotificationMode.ANY_DECREASE) {
            return "сообщать о новой минимальной цене";
        }
        return "сообщить при цене " + format(item.getTargetPrice().orElseThrow());
    }

    private int pageCount(int itemCount) {
        return (itemCount + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
    }

    private void validateItems(List<TrackedSubscriptionItem> items) {
        if (items == null || items.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("tracked items must not contain nulls");
        }
    }

    private String format(RubleAmount amount) {
        return RublePriceFormatter.format(amount);
    }

    private String truncate(String value, int maxLength) {
        if (value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        int endIndex = value.offsetByCodePoints(0, maxLength - 1);
        return value.substring(0, endIndex) + "…";
    }
}
