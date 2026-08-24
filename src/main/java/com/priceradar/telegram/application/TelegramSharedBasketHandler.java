package com.priceradar.telegram.application;

import com.priceradar.sharedbasket.application.SharedBasketApplyResult;
import com.priceradar.sharedbasket.application.SharedBasketImportService;
import com.priceradar.sharedbasket.application.SharedBasketPreview;
import com.priceradar.sharedbasket.application.SharedBasketPreviewResult;
import com.priceradar.sharedbasket.application.SharedBasketUrlParser;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TelegramSharedBasketHandler {

    private final SharedBasketUrlParser urlParser;
    private final SharedBasketImportService importService;
    private final SharedBasketCallbackCodec callbackCodec;
    private final UserProfileService userProfileService;
    private final TelegramGateway telegramGateway;
    private final Clock clock;

    public TelegramSharedBasketHandler(
            SharedBasketUrlParser urlParser,
            SharedBasketImportService importService,
            SharedBasketCallbackCodec callbackCodec,
            UserProfileService userProfileService,
            TelegramGateway telegramGateway,
            Clock clock
    ) {
        this.urlParser = java.util.Objects.requireNonNull(urlParser);
        this.importService = java.util.Objects.requireNonNull(importService);
        this.callbackCodec = java.util.Objects.requireNonNull(callbackCodec);
        this.userProfileService = java.util.Objects.requireNonNull(userProfileService);
        this.telegramGateway = java.util.Objects.requireNonNull(telegramGateway);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public boolean handleMessage(IncomingTelegramMessage message) {
        if (!message.isPrivateChat() || !urlParser.supports(message.getText())) return false;
        UserProfile user = userProfileService.getOrCreate(message.getTelegramUserId(), message.getChatId());
        SharedBasketPreviewResult result = importService.prepare(message.getText(), user, clock.instant());
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
        switch (data.getAction()) {
            case BACK -> telegramGateway.sendMessage(previewMessage(
                    callback.getChatId(), callback.getTelegramUserId(), preview.orElseThrow()
            ));
            case ADD -> handleAdd(callback, user, preview.orElseThrow(), false);
            case ADD_CONFIRM -> handleAdd(callback, user, preview.orElseThrow(), true);
            case SYNC -> handleSync(callback, user, preview.orElseThrow(), false);
            case SYNC_CONFIRM -> handleSync(callback, user, preview.orElseThrow(), true);
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
            String text = "⚠️ Недостаточно свободных мест\n\n"
                    + "Свободно: " + preview.getFreeSlots() + ".\n"
                    + "Найдено новых товаров: " + preview.getNewItems() + ".\n"
                    + "Будут добавлены первые " + preview.getAddableItems() + ".\n"
                    + "Оставшиеся " + preview.getAddSkippedByLimit() + " будут пропущены.";
            telegramGateway.sendMessage(confirmMessage(
                    callback.getChatId(), callback.getTelegramUserId(), preview.getImportId(), text,
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
            boolean confirmed
    ) {
        if (!confirmed && preview.getMissingTracked() > 0) {
            StringBuilder text = new StringBuilder("⚠️ Подтвердите синхронизацию\n\n")
                    .append("Будет прекращено отслеживание ").append(preview.getMissingTracked())
                    .append(" товаров, которых нет в переданной корзине.\n")
                    .append("При повторном добавлении начнётся новый период статистики.\n\n")
                    .append("Будет прекращено отслеживание:\n");
            preview.getMissingTitles().stream().limit(5).forEach(title -> text.append("• ").append(title).append('\n'));
            if (preview.getMissingTracked() > 5) text.append("и ещё ").append(preview.getMissingTracked() - 5).append('\n');
            telegramGateway.sendMessage(confirmMessage(
                    callback.getChatId(), callback.getTelegramUserId(), preview.getImportId(), text.toString(),
                    "Да, синхронизировать", SharedBasketCallbackCodec.Action.SYNC_CONFIRM
            ));
            return;
        }
        telegramGateway.sendMessage(appliedMessage(
                callback.getChatId(),
                importService.apply(preview.getImportId(), user.getId(), SharedBasketImportService.ApplyMode.SYNCHRONIZE, clock.instant())
        ));
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
                .append("Найдено товаров: ").append(preview.getFoundItems()).append('\n')
                .append("Готово к импорту: ").append(preview.getReadyItems()).append('\n')
                .append("Уже отслеживаются: ").append(preview.getAlreadyTracked()).append('\n')
                .append("Новых: ").append(preview.getNewItems()).append('\n')
                .append("Отслеживаются, но отсутствуют в корзине: ").append(preview.getMissingTracked()).append('\n');
        if (preview.getSkippedItems() > 0) {
            text.append("Не удалось обработать: ").append(preview.getSkippedItems()).append(" — они будут пропущены.\n");
        }
        if (preview.getSyncSkippedByLimit() > 0) {
            text.append("\n⚠️ Лимит отслеживания — 50. При синхронизации будут использованы первые ")
                    .append(preview.getSyncTargetItems()).append(" товаров, остальные ")
                    .append(preview.getSyncSkippedByLimit()).append(" будут пропущены.");
        }
        List<List<TelegramInlineButton>> keyboard = List.of(
                List.of(button("Добавить новые", SharedBasketCallbackCodec.Action.ADD, preview, telegramUserId)),
                List.of(button("Синхронизировать", SharedBasketCallbackCodec.Action.SYNC, preview, telegramUserId)),
                List.of(button("Отмена", SharedBasketCallbackCodec.Action.CANCEL, preview, telegramUserId))
        );
        return new OutgoingTelegramMessage(chatId, text.toString(), keyboard);
    }

    private OutgoingTelegramMessage confirmMessage(
            long chatId, long telegramUserId, java.util.UUID importId, String text,
            String confirmText, SharedBasketCallbackCodec.Action action
    ) {
        return new OutgoingTelegramMessage(chatId, text, List.of(
                List.of(new TelegramInlineButton(confirmText, callbackCodec.encode(action, importId, telegramUserId))),
                List.of(new TelegramInlineButton("Назад", callbackCodec.encode(
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
        if (result.getStatus() != SharedBasketApplyResult.Status.APPLIED) return expired(chatId);
        String text = "Корзина обработана.\n"
                + "Добавлено: " + result.getAdded() + ".\n"
                + "Сохранено без изменений: " + result.getKept() + ".\n"
                + "Отслеживание прекращено: " + result.getEnded() + "."
                + (result.getSkippedByLimit() > 0
                ? "\nПропущено из-за лимита: " + result.getSkippedByLimit() + "." : "");
        return message(chatId, text);
    }

    private OutgoingTelegramMessage expired(long chatId) {
        return message(chatId, "Импорт устарел. Отправьте ссылку на корзину ещё раз.");
    }

    private OutgoingTelegramMessage message(long chatId, String text) {
        return new OutgoingTelegramMessage(chatId, text, TelegramNavigationKeyboard.mainMenu());
    }
}
