package com.priceradar.telegram.application;

import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.statistics.domain.StatisticsPeriod;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.tracking.domain.NotificationMode;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TrackedItemsMessageFactory {

    private static final int ITEMS_PER_MESSAGE = 8;
    private static final int MAX_TITLE_LENGTH = 90;
    private static final int MAX_DETAIL_LENGTH = 60;
    private static final String APPROXIMATE_PRICE_WARNING =
            "⚠️ Цены приблизительные и могут отличаться в вашем аккаунте Wildberries.";
    private static final DateTimeFormatter OBSERVED_AT_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm 'UTC'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    public List<OutgoingTelegramMessage> create(
            long chatId,
            List<TrackedSubscriptionItem> items,
            String region
    ) {
        if (items == null || region == null || region.isBlank()) {
            throw new IllegalArgumentException("tracked items message fields must not be null or blank");
        }
        if (items.isEmpty()) {
            return List.of(OutgoingTelegramMessage.text(
                    chatId,
                    "У вас пока нет отслеживаемых товаров. Отправьте ссылку Wildberries, "
                            + "чтобы добавить первый товар."
            ));
        }

        List<OutgoingTelegramMessage> messages = new ArrayList<>();
        int pageCount = (items.size() + ITEMS_PER_MESSAGE - 1) / ITEMS_PER_MESSAGE;
        for (int from = 0, page = 1; from < items.size(); from += ITEMS_PER_MESSAGE, page++) {
            int to = Math.min(from + ITEMS_PER_MESSAGE, items.size());
            messages.add(createPage(
                    chatId,
                    items.subList(from, to),
                    region.trim(),
                    from,
                    page,
                    pageCount,
                    items.size()
            ));
        }
        return List.copyOf(messages);
    }

    private OutgoingTelegramMessage createPage(
            long chatId,
            List<TrackedSubscriptionItem> items,
            String region,
            int offset,
            int page,
            int pageCount,
            int totalCount
    ) {
        StringBuilder text = new StringBuilder("Отслеживаемые товары: ")
                .append(totalCount);
        if (pageCount > 1) {
            text.append(" · страница ").append(page).append('/').append(pageCount);
        }

        List<List<TelegramInlineButton>> keyboard = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            int displayNumber = offset + index + 1;
            TrackedSubscriptionItem item = items.get(index);
            appendItem(text, item, displayNumber);
            keyboard.add(List.of(
                    new TelegramInlineButton(
                            "Последняя цена #" + displayNumber,
                            "SHOW_LAST_KNOWN:" + item.getSubscriptionId()
                    ),
                    new TelegramInlineButton(
                            "Удалить #" + displayNumber,
                            "REMOVE_TRACKING:" + item.getSubscriptionId()
                    )
            ));
            keyboard.add(List.of(
                    statisticsButton(
                            "7 дней #" + displayNumber,
                            item,
                            StatisticsPeriod.LAST_7_DAYS
                    ),
                    statisticsButton(
                            "30 дней #" + displayNumber,
                            item,
                            StatisticsPeriod.LAST_30_DAYS
                    )
            ));
            keyboard.add(List.of(
                    statisticsButton(
                            "365 дней #" + displayNumber,
                            item,
                            StatisticsPeriod.LAST_365_DAYS
                    ),
                    statisticsButton(
                            "Всё время #" + displayNumber,
                            item,
                            StatisticsPeriod.ALL_TIME
                    )
            ));
        }
        text.append("\n\nРегион: ").append(region);
        text.append("\n").append(APPROXIMATE_PRICE_WARNING);
        return new OutgoingTelegramMessage(chatId, text.toString(), keyboard);
    }

    private TelegramInlineButton statisticsButton(
            String text,
            TrackedSubscriptionItem item,
            StatisticsPeriod period
    ) {
        return new TelegramInlineButton(
                text,
                StatisticsCallbackData.encode(item.getSubscriptionId(), period)
        );
    }

    private void appendItem(
            StringBuilder text,
            TrackedSubscriptionItem item,
            int displayNumber
    ) {
        text.append("\n\n").append(displayNumber).append(". ")
                .append(truncate(
                        item.getTitle().orElse("Товар Wildberries #" + item.getNmId()),
                        MAX_TITLE_LENGTH
                ));
        item.getBrand().ifPresent(brand -> text.append("\nБренд: ")
                .append(truncate(brand, MAX_DETAIL_LENGTH)));
        item.getVariantDisplayName().ifPresent(variant -> text.append("\nВариант: ")
                .append(truncate(variant, MAX_DETAIL_LENGTH)));
        text.append("\nРежим: ").append(mode(item));
        text.append("\nПоследнее состояние: ").append(latestState(item));
        item.getLatestObservedAt().ifPresent(observedAt -> text.append(" · ")
                .append(OBSERVED_AT_FORMAT.format(observedAt)));
        text.append("\n").append(item.getCanonicalUrl());
    }

    private String mode(TrackedSubscriptionItem item) {
        if (item.getNotificationMode() == NotificationMode.ANY_DECREASE) {
            return "любое снижение обычной цены";
        }
        return "целевая цена " + format(item.getTargetPrice().orElseThrow());
    }

    private String latestState(TrackedSubscriptionItem item) {
        if (item.getLatestSnapshotStatus().isEmpty()) {
            return "наблюдений пока нет";
        }
        SnapshotStatus status = item.getLatestSnapshotStatus().orElseThrow();
        return switch (status) {
            case REGULAR_PRICE -> item.getLatestRegularPrice()
                    .map(this::format)
                    .orElse("обычная цена недоступна");
            case BASIC_FALLBACK ->
                    "обычная цена недоступна, есть только маркетинговая цена (fallback)";
            case UNAVAILABLE -> "товар недоступен";
            case NO_PRICE -> "цена не найдена";
        };
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
