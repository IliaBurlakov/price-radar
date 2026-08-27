package com.priceradar.telegram.application;

import com.priceradar.sharedbasket.application.SharedBasketApplyResult;
import com.priceradar.sharedbasket.application.SharedBasketImportService;
import com.priceradar.sharedbasket.application.SharedBasketPreview;
import com.priceradar.sharedbasket.application.SharedBasketPreviewResult;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TelegramSharedBasketHandler {

    private static final int UNAVAILABLE_DISPLAY_LIMIT = 5;
    private static final String PROCESSING_MESSAGE = "⏳ Ваш запрос обрабатывается...";

    private final WildberriesLinkExtractor linkExtractor;
    private final SharedBasketImportService importService;
    private final SharedBasketCallbackCodec callbackCodec;
    private final UserProfileService userProfileService;
    private final TelegramGateway telegramGateway;
    private final Clock clock;
    private final int activeSubscriptionLimit;

    public TelegramSharedBasketHandler(
            WildberriesLinkExtractor linkExtractor,
            SharedBasketImportService importService,
            SharedBasketCallbackCodec callbackCodec,
            UserProfileService userProfileService,
            TelegramGateway telegramGateway,
            Clock clock,
            int activeSubscriptionLimit
    ) {
        this.linkExtractor = java.util.Objects.requireNonNull(linkExtractor);
        this.importService = java.util.Objects.requireNonNull(importService);
        this.callbackCodec = java.util.Objects.requireNonNull(callbackCodec);
        this.userProfileService = java.util.Objects.requireNonNull(userProfileService);
        this.telegramGateway = java.util.Objects.requireNonNull(telegramGateway);
        this.clock = java.util.Objects.requireNonNull(clock);
        if (activeSubscriptionLimit <= 0) {
            throw new IllegalArgumentException("active subscription limit must be positive");
        }
        this.activeSubscriptionLimit = activeSubscriptionLimit;
    }

    public boolean handleMessage(IncomingTelegramMessage message) {
        if (!message.isPrivateChat()) return false;
        Optional<String> basketUrl = linkExtractor.extract(message.getText()).getSharedBasketUrl();
        if (basketUrl.isEmpty()) return false;
        telegramGateway.sendMessage(OutgoingTelegramMessage.text(
                message.getChatId(), PROCESSING_MESSAGE
        ));
        UserProfile user = userProfileService.getOrCreate(message.getTelegramUserId(), message.getChatId());
        SharedBasketPreviewResult result = importService.prepare(basketUrl.orElseThrow(), user, clock.instant());
        telegramGateway.sendMessage(preparationMessage(message.getChatId(), message.getTelegramUserId(), result));
        return true;
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        if (!callback.isPrivateChat()) return false;
        Optional<SharedBasketCallbackCodec.Decoded> decoded = callbackCodec.decode(
                callback.getData(), callback.getTelegramUserId()
        );
        if (decoded.isEmpty()) return false;

        UserProfile user = userProfileService.getOrCreate(callback.getTelegramUserId(), callback.getChatId());
        SharedBasketCallbackCodec.Decoded data = decoded.orElseThrow();
        if (data.getAction() == SharedBasketCallbackCodec.Action.CANCEL) {
            importService.cancel(data.getImportId(), user.getId());
            telegramGateway.sendMessage(message(callback.getChatId(), "Импорт отменён."));
            return true;
        }
        Optional<SharedBasketPreview> preview = importService.findPreview(
                data.getImportId(), user.getId(), clock.instant()
        );
        if (preview.isEmpty()) {
            telegramGateway.sendMessage(expired(callback.getChatId()));
            return true;
        }
        if (preview.orElseThrow().getRegionCode() != user.getRegion().getCode()) {
            telegramGateway.sendMessage(previousRegionImport(callback.getChatId()));
            return true;
        }
        switch (data.getAction()) {
            case BACK -> telegramGateway.sendMessage(previewMessage(
                    callback.getChatId(), callback.getTelegramUserId(), preview.orElseThrow()
            ));
            case ADD -> handleAdd(callback, user, preview.orElseThrow(), false);
            case ADD_CONFIRM -> handleAdd(callback, user, preview.orElseThrow(), true);
            case SYNC -> handleSync(callback, user, preview.orElseThrow(), false, Optional.empty());
            case SYNC_CONFIRM -> handleSync(
                    callback, user, preview.orElseThrow(), true, data.getPlanFingerprint()
            );
            case CANCEL -> throw new IllegalStateException("cancel was already handled");
        }
        return true;
    }

    private void handleAdd(
            IncomingTelegramCallback callback,
            UserProfile user,
            SharedBasketPreview preview,
            boolean confirmed
    ) {
        if (!confirmed && preview.getAddSkippedByLimit() > 0) {
            StringBuilder text = new StringBuilder("⚠️ Недостаточно свободных мест\n\n")
                    .append("Свободно: ").append(preview.getFreeSlots()).append(".\n")
                    .append("Найдено новых товаров: ").append(preview.getNewItems()).append(".\n")
                    .append("Будут добавлены первые ").append(preview.getAddableItems()).append(".\n\n")
                    .append("Не войдут в отслеживание:\n");
            appendProductTitles(text, preview.getAddSkippedTitles());
            telegramGateway.sendMessage(confirmMessage(
                    callback.getChatId(), callback.getTelegramUserId(), preview.getImportId(), text.toString(),
                    "Продолжить", SharedBasketCallbackCodec.Action.ADD_CONFIRM
            ));
            return;
        }
        telegramGateway.sendMessage(appliedMessage(
                callback.getChatId(),
                importService.apply(preview.getImportId(), user.getId(), SharedBasketImportService.ApplyMode.ADD_NEW, clock.instant())
        ));
    }

    private void handleSync(
            IncomingTelegramCallback callback,
            UserProfile user,
            SharedBasketPreview preview,
            boolean confirmed,
            Optional<String> confirmedPlanFingerprint
    ) {
        if (!preview.isSynchronizationAvailable()) {
            telegramGateway.sendMessage(synchronizationUnavailable(callback.getChatId(), preview));
            return;
        }
        if (!confirmed && preview.getDestructiveRemovalCount() > 0) {
            StringBuilder text = new StringBuilder("⚠️ Подтвердите синхронизацию\n");
            if (preview.getAbsentTracked() > 0) {
                text.append("\nСейчас вы отслеживаете ")
                        .append(items(preview.getAbsentTracked())).append(",\n")
                        .append(relativePronoun(preview.getAbsentTracked())).append(" нет в этой корзине.\n\n")
                        .append("После синхронизации их отслеживание будет остановлено:\n");
                appendRemovalTitles(text, preview.getAbsentTitles());
            }
            if (preview.getExcludedByLimit() > 0) {
                appendLimitRemovalWarning(
                        text, preview.getExcludedByLimit(), preview.getExcludedByLimitTitles()
                );
            }
            text.append("\nℹ️ Некоторые товары могли отслеживаться долгое время.\n")
                    .append("При прекращении подписки текущий период отслеживания завершится.\n")
                    .append("При повторном добавлении начнётся новый период отслеживания и статистики.");
            telegramGateway.sendMessage(confirmMessage(
                    callback.getChatId(), callback.getTelegramUserId(), preview.getImportId(), text.toString(),
                    "Да, синхронизировать", SharedBasketCallbackCodec.Action.SYNC_CONFIRM,
                    Optional.of(preview.getDestructivePlanFingerprint())
            ));
            return;
        }
        SharedBasketApplyResult result = importService.apply(
                preview.getImportId(), user.getId(), SharedBasketImportService.ApplyMode.SYNCHRONIZE,
                confirmedPlanFingerprint, clock.instant()
        );
        if (result.getStatus() == SharedBasketApplyResult.Status.PLAN_CHANGED) {
            Optional<SharedBasketPreview> refreshed = importService.findPreview(
                    preview.getImportId(), user.getId(), clock.instant()
            );
            if (refreshed.isEmpty()) telegramGateway.sendMessage(expired(callback.getChatId()));
            else handleSync(callback, user, refreshed.orElseThrow(), false, Optional.empty());
            return;
        }
        telegramGateway.sendMessage(appliedMessage(callback.getChatId(), result));
    }

    private OutgoingTelegramMessage preparationMessage(
            long chatId,
            long telegramUserId,
            SharedBasketPreviewResult result
    ) {
        return switch (result.getStatus()) {
            case READY -> previewMessage(chatId, telegramUserId, result.getPreview().orElseThrow());
            case INVALID_URL -> message(chatId, "Не получилось распознать ссылку на корзину Wildberries.");
            case NOT_FOUND -> message(chatId, "Не удалось найти эту корзину Wildberries. Возможно, ссылка устарела.");
            case TEMPORARILY_UNAVAILABLE -> message(chatId, "Wildberries временно недоступен. Попробуйте импорт ещё раз позже.");
        };
    }

    private OutgoingTelegramMessage previewMessage(long chatId, long telegramUserId, SharedBasketPreview preview) {
        StringBuilder text = new StringBuilder("🛒 Корзина Wildberries\n\n")
                .append("Найдено товаров: ").append(preview.getFoundItems());
        if (preview.getAlreadyTracked() > 0) {
            text.append("\nУже отслеживаются: ").append(preview.getAlreadyTracked());
        }
        if (preview.getNewItems() > 0) {
            text.append("\nНовых: ").append(preview.getNewItems());
        }
        if (preview.getAbsentTracked() > 0) {
            text.append("\n\nПри синхронизации бот перестанет отслеживать\n")
                    .append(items(preview.getAbsentTracked())).append(", ")
                    .append(relativePronoun(preview.getAbsentTracked())).append(" нет в этой корзине.");
        }
        if (preview.getUnavailableItems() > 0) {
            appendUnavailableItems(text, preview.getUnavailableTitles());
        }
        if (preview.getUnresolvedItems() > 0) {
            text.append("\n\n⚠️ Ещё ").append(items(preview.getUnresolvedItems()))
                    .append(" не удалось обработать.\n")
                    .append("Попробуйте повторить импорт позже.");
        }
        if (!preview.isSynchronizationAvailable()) {
            text.append("\n\n🔄 Синхронизация сейчас недоступна.\n\n")
                    .append(synchronizationUnavailableReason(preview))
                    .append("\n\n")
                    .append("Можно добавить остальные новые товары или повторить импорт позже.");
        }
        if (preview.getSyncSkippedByLimit() > 0) {
            text.append("\n\n⚠️ Лимит отслеживания — ").append(activeSubscriptionLimit).append(" товаров.\n")
                    .append(items(preview.getSyncSkippedByLimit())).append(" из этой корзины ")
                    .append(isSingularCount(preview.getSyncSkippedByLimit()) ? "не войдёт" : "не войдут")
                    .append(" в отслеживание:\n");
            appendProductTitles(text, preview.getSyncSkippedTitles());
        }
        List<List<TelegramInlineButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button("Добавить новые", SharedBasketCallbackCodec.Action.ADD, preview, telegramUserId)));
        if (preview.isSynchronizationAvailable()) {
            keyboard.add(List.of(button(
                    "Синхронизировать", SharedBasketCallbackCodec.Action.SYNC, preview, telegramUserId
            )));
        }
        keyboard.add(List.of(button("Отмена", SharedBasketCallbackCodec.Action.CANCEL, preview, telegramUserId)));
        return new OutgoingTelegramMessage(chatId, text.toString(), keyboard);
    }

    private void appendRemovalTitles(StringBuilder text, List<String> titles) {
        titles.stream().limit(5).forEach(title -> text.append("• ").append(title).append('\n'));
        if (titles.size() > 5) text.append("• и ещё ").append(titles.size() - 5).append('\n');
    }

    private void appendUnavailableItems(StringBuilder text, List<String> titles) {
        int count = titles.size();
        text.append("\n\n⚠️ Сейчас ")
                .append(isSingularCount(count) ? "недоступен " : "недоступны ")
                .append(items(count)).append(":\n\n");
        titles.stream().limit(UNAVAILABLE_DISPLAY_LIMIT)
                .forEach(title -> text.append("• ").append(title).append('\n'));
        if (count > UNAVAILABLE_DISPLAY_LIMIT) {
            text.append("• и ещё ").append(count - UNAVAILABLE_DISPLAY_LIMIT).append('\n');
        }
        text.append('\n').append(isSingularCount(count)
                ? "Он не будет добавлен." : "Они не будут добавлены.");
    }

    private void appendLimitRemovalWarning(StringBuilder text, int count, List<String> titles) {
        text.append("\n⚠️ Ещё ").append(count);
        if (isSingularCount(count)) {
            text.append(" отслеживаемый товар есть в корзине,\n")
                    .append("но находится после первых ").append(activeSubscriptionLimit)
                    .append(" и поэтому тоже будет удалён из отслеживания:\n");
            appendProductTitles(text, titles);
            return;
        }
        text.append(" отслеживаемых ").append(itemWord(count)).append(" есть в корзине,\n")
                .append("но находятся после первых ").append(activeSubscriptionLimit)
                .append(" и поэтому тоже будут удалены из отслеживания:\n");
        appendProductTitles(text, titles);
    }

    private void appendProductTitles(StringBuilder text, List<String> titles) {
        titles.forEach(title -> text.append("• ").append(title).append('\n'));
    }

    private OutgoingTelegramMessage confirmMessage(
            long chatId, long telegramUserId, java.util.UUID importId, String text,
            String confirmText, SharedBasketCallbackCodec.Action action
    ) {
        return confirmMessage(chatId, telegramUserId, importId, text, confirmText, action, Optional.empty());
    }

    private OutgoingTelegramMessage confirmMessage(
            long chatId, long telegramUserId, java.util.UUID importId, String text,
            String confirmText, SharedBasketCallbackCodec.Action action, Optional<String> planFingerprint
    ) {
        String confirmCallback = action == SharedBasketCallbackCodec.Action.SYNC_CONFIRM
                ? callbackCodec.encodeSyncConfirm(importId, planFingerprint.orElseThrow(), telegramUserId)
                : callbackCodec.encode(action, importId, telegramUserId);
        return new OutgoingTelegramMessage(chatId, text, List.of(
                List.of(new TelegramInlineButton(confirmText, confirmCallback)),
                List.of(new TelegramInlineButton("← Назад", callbackCodec.encode(
                        SharedBasketCallbackCodec.Action.BACK, importId, telegramUserId
                )))
        ));
    }

    private TelegramInlineButton button(
            String text, SharedBasketCallbackCodec.Action action,
            SharedBasketPreview preview, long telegramUserId
    ) {
        return new TelegramInlineButton(text, callbackCodec.encode(action, preview.getImportId(), telegramUserId));
    }

    private OutgoingTelegramMessage appliedMessage(long chatId, SharedBasketApplyResult result) {
        if (result.getStatus() == SharedBasketApplyResult.Status.REGION_MISMATCH) {
            return previousRegionImport(chatId);
        }
        if (result.getStatus() != SharedBasketApplyResult.Status.APPLIED) return expired(chatId);
        StringBuilder text = new StringBuilder("Корзина обработана.");
        if (result.getAdded() > 0) text.append("\n\nДобавлено: ").append(result.getAdded()).append('.');
        if (result.getKept() > 0) text.append("\nБез изменений: ").append(result.getKept()).append('.');
        if (result.getEnded() > 0) text.append("\nУдалено из отслеживания: ").append(result.getEnded()).append('.');
        if (result.getSkippedByLimit() > 0) {
            text.append("\n\nНе вошли в отслеживание из-за лимита:\n");
            appendProductTitles(text, result.getSkippedByLimitTitles());
        }
        if (result.getAdded() == 0 && result.getKept() == 0 && result.getEnded() == 0) {
            text.append("\n\nИзменений нет.");
        }
        return message(chatId, text.toString());
    }

    private OutgoingTelegramMessage expired(long chatId) {
        return message(chatId, "Импорт устарел. Отправьте ссылку на корзину ещё раз.");
    }

    private OutgoingTelegramMessage previousRegionImport(long chatId) {
        return message(chatId, "Этот импорт относится к предыдущему городу. "
                + "Отправьте ссылку на корзину ещё раз.");
    }

    private OutgoingTelegramMessage synchronizationUnavailable(long chatId, SharedBasketPreview preview) {
        return message(chatId, "🔄 Синхронизация сейчас недоступна.\n\n"
                + synchronizationUnavailableReason(preview)
                + "\n\nМожно добавить остальные новые товары или повторить импорт позже.");
    }

    private String synchronizationUnavailableReason(SharedBasketPreview preview) {
        if (preview.getUnavailableItems() > 0 && preview.getUnresolvedItems() > 0) {
            return "В корзине есть товары, которые Wildberries сейчас показывает как недоступные, "
                    + "а часть товаров не удалось обработать.";
        }
        if (preview.getUnavailableItems() > 0) {
            return "В корзине есть недоступные товары, "
                    + "поэтому бот не может безопасно определить полный список для синхронизации.";
        }
        return "Часть товаров не удалось обработать, поэтому бот не может безопасно определить "
                + "полный список для синхронизации.";
    }

    private OutgoingTelegramMessage message(long chatId, String text) {
        return new OutgoingTelegramMessage(chatId, text, TelegramNavigationKeyboard.mainMenu());
    }

    private String items(int count) {
        return count + " " + itemWord(count);
    }

    private String itemWord(int count) {
        int lastTwo = Math.abs(count) % 100;
        int last = lastTwo % 10;
        if (lastTwo >= 11 && lastTwo <= 14) return "товаров";
        if (last == 1) return "товар";
        if (last >= 2 && last <= 4) return "товара";
        return "товаров";
    }

    private String relativePronoun(int count) {
        return isSingularCount(count) ? "которого" : "которых";
    }

    private boolean isSingularCount(int count) {
        return itemWord(count).equals("товар");
    }
}
