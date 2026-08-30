package com.priceradar.telegram.application;

import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.application.ResolvedQuote;

import java.util.ArrayList;
import java.util.List;

public final class TelegramMultiProductQuoteMessageFactory {

    public static final int PAGE_SIZE = 8;

    private final MultiProductQuoteCallbackCodec callbackCodec;
    private final int maxProductLinksPerMessage;

    public TelegramMultiProductQuoteMessageFactory(
            MultiProductQuoteCallbackCodec callbackCodec,
            int maxProductLinksPerMessage
    ) {
        this.callbackCodec = java.util.Objects.requireNonNull(callbackCodec);
        if (maxProductLinksPerMessage <= 0) {
            throw new IllegalArgumentException("product link limit must be positive");
        }
        this.maxProductLinksPerMessage = maxProductLinksPerMessage;
    }

    public OutgoingTelegramMessage createPage(
            long chatId,
            long telegramUserId,
            MultiProductQuoteSession session,
            int requestedPage,
            boolean truncated,
            int invalidUrlCount
    ) {
        int pageCount = Math.max(1, (session.getItems().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, session.getItems().size());

        StringBuilder text = new StringBuilder("📦 Найдено товаров: ")
                .append(session.getItems().size());
        if (pageCount > 1) {
            text.append("\nСтраница ").append(page + 1).append(" из ").append(pageCount);
        }
        text.append("\n\n");
        for (MultiProductQuoteItem item : session.getItems().subList(from, to)) {
            appendItem(text, item);
        }
        if (session.getItems().stream().anyMatch(item -> item.getQuote().isPresent())) {
            text.append("\nВыберите товар, чтобы открыть его и настроить отслеживание.");
        }
        if (truncated) {
            text.append("\n\n⚠️ В одном сообщении можно обработать не более ")
                    .append(maxProductLinksPerMessage)
                    .append(" товаров. Остальные ссылки пропущены.");
        }
        if (invalidUrlCount > 0) {
            text.append("\n\n⚠️ Некоторые ссылки Wildberries не удалось распознать.");
        }

        List<List<TelegramInlineButton>> keyboard = new ArrayList<>();
        List<TelegramInlineButton> itemRow = new ArrayList<>();
        for (MultiProductQuoteItem item : session.getItems().subList(from, to)) {
            if (item.getQuote().isEmpty()) continue;
            itemRow.add(new TelegramInlineButton(
                    String.valueOf(item.getPosition() + 1),
                    callbackCodec.encode(
                            MultiProductQuoteCallbackCodec.Action.ITEM,
                            session.getId(), item.getPosition(), telegramUserId
                    )
            ));
            if (itemRow.size() == 4) {
                keyboard.add(List.copyOf(itemRow));
                itemRow.clear();
            }
        }
        if (!itemRow.isEmpty()) keyboard.add(List.copyOf(itemRow));

        List<TelegramInlineButton> navigation = new ArrayList<>();
        if (page > 0) navigation.add(new TelegramInlineButton(
                "←", callbackCodec.encode(MultiProductQuoteCallbackCodec.Action.PAGE,
                        session.getId(), page - 1, telegramUserId)
        ));
        if (page + 1 < pageCount) navigation.add(new TelegramInlineButton(
                "→", callbackCodec.encode(MultiProductQuoteCallbackCodec.Action.PAGE,
                        session.getId(), page + 1, telegramUserId)
        ));
        if (!navigation.isEmpty()) keyboard.add(List.copyOf(navigation));
        if (session.getItems().stream().anyMatch(item -> item.getQuote().isPresent())) {
            keyboard.add(List.of(new TelegramInlineButton(
                    "Следить за минимумом для всех",
                    callbackCodec.encode(
                            MultiProductQuoteCallbackCodec.Action.ALL_MINIMUM,
                            session.getId(), 0, telegramUserId
                    )
            )));
        }
        keyboard.addAll(TelegramNavigationKeyboard.addProductAndHome());
        return new OutgoingTelegramMessage(chatId, text.toString(), keyboard);
    }

    private void appendItem(StringBuilder text, MultiProductQuoteItem item) {
        int number = item.getPosition() + 1;
        String displayName = abbreviated(item.getDisplayName());
        if (item.getStatus() == MultiProductQuoteItem.Status.UNAVAILABLE) {
            text.append(number).append(". ⚠️ Сейчас недоступен: ")
                    .append(displayName).append('\n');
            return;
        }
        if (item.getStatus() == MultiProductQuoteItem.Status.UNRESOLVED) {
            text.append(number).append(". Не удалось обработать: ")
                    .append(displayName).append('\n');
            return;
        }
        ResolvedQuote quote = item.getQuote().orElseThrow();
        text.append(number).append(". ").append(displayName);
        if (quote.getInterpretedPrice().getStatus() == SnapshotStatus.REGULAR_PRICE) {
            text.append(" — ").append(RublePriceFormatter.format(
                    quote.getInterpretedPrice().getRegularPrice().orElseThrow()
            ));
        } else if (quote.getInterpretedPrice().getStatus() == SnapshotStatus.BASIC_FALLBACK) {
            text.append(" — ").append(RublePriceFormatter.format(
                    quote.getInterpretedPrice().getMarketingBasePrice().orElseThrow()
            ));
        } else {
            text.append(" — цена недоступна");
        }
        text.append('\n');
    }

    private String abbreviated(String value) {
        int maximumCodePoints = 80;
        if (value.codePointCount(0, value.length()) <= maximumCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maximumCodePoints - 1);
        return value.substring(0, end).stripTrailing() + "…";
    }
}
