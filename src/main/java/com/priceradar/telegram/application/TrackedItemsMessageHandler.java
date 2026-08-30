package com.priceradar.telegram.application;

import com.priceradar.tracking.application.SubscriptionEndResult;
import com.priceradar.tracking.application.ClearSubscriptionsPlan;
import com.priceradar.tracking.application.ClearSubscriptionsResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.application.SubscriptionConditionChangeResult;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public class TrackedItemsMessageHandler {

    private static final Duration TARGET_INPUT_TTL = Duration.ofMinutes(15);

    private final UserProfileService userProfileService;
    private final SubscriptionService subscriptionService;
    private final TrackedItemsMessageFactory messageFactory;
    private final TelegramGateway telegramGateway;
    private final ClearTrackingCallbackCodec clearCallbackCodec;
    private final PendingTargetPriceStore pendingTargetPriceStore;
    private final Clock clock;

    public TrackedItemsMessageHandler(
            UserProfileService userProfileService,
            SubscriptionService subscriptionService,
            TrackedItemsMessageFactory messageFactory,
            ClearTrackingCallbackCodec clearCallbackCodec,
            PendingTargetPriceStore pendingTargetPriceStore,
            TelegramGateway telegramGateway,
            Clock clock
    ) {
        if (userProfileService == null || subscriptionService == null || messageFactory == null
                || clearCallbackCodec == null
                || pendingTargetPriceStore == null
                || telegramGateway == null || clock == null) {
            throw new IllegalArgumentException("tracked items handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.subscriptionService = subscriptionService;
        this.messageFactory = messageFactory;
        this.clearCallbackCodec = clearCallbackCodec;
        this.pendingTargetPriceStore = pendingTargetPriceStore;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
    }

    public boolean handleMessage(IncomingTelegramMessage message) {
        if (!message.isPrivateChat() || !isTrackedCommand(message.getText())) {
            return false;
        }
        showTracked(message.getTelegramUserId(), message.getChatId());
        return true;
    }

    public void showTracked(long telegramUserId, long chatId) {
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        UserProfile profile = userProfileService.getOrCreate(
                telegramUserId,
                chatId
        );
        List<TrackedSubscriptionItem> items = subscriptionService.findActive(profile.getId());
        telegramGateway.sendMessage(messageFactory.createList(chatId, items, 0));
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        Optional<String> clearFingerprint = clearCallbackCodec.decodeConfirm(
                callback.getData(), callback.getTelegramUserId()
        );
        boolean clearStart = ClearTrackingCallbackData.START.equals(callback.getData());
        Optional<UUID> removeId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.REMOVE,
                callback.getData()
        );
        Optional<UUID> confirmRemoveId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.CONFIRM_REMOVE,
                callback.getData()
        );
        Optional<UUID> itemId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.OPEN_ITEM,
                callback.getData()
        );
        Optional<UUID> conditionId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.SHOW_NOTIFICATION_CONDITION,
                callback.getData()
        );
        Optional<UUID> setAnyId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.SET_ANY_DECREASE,
                callback.getData()
        );
        Optional<UUID> editTargetId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.EDIT_TARGET_PRICE,
                callback.getData()
        );
        Optional<UUID> cancelConditionId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.CANCEL_CONDITION_EDIT,
                callback.getData()
        );
        OptionalInt requestedPage = TrackedItemsPageCallbackData.parse(callback.getData());
        if (!clearStart && clearFingerprint.isEmpty()
                && removeId.isEmpty() && confirmRemoveId.isEmpty()
                && itemId.isEmpty() && requestedPage.isEmpty()
                && conditionId.isEmpty() && setAnyId.isEmpty()
                && editTargetId.isEmpty() && cancelConditionId.isEmpty()) {
            return false;
        }

        if (!callback.isPrivateChat()) {
            return true;
        }
        UserProfile profile = userProfileService.getOrCreate(
                callback.getTelegramUserId(),
                callback.getChatId()
        );
        if (clearStart) {
            showClearConfirmation(callback.getChatId(), callback.getTelegramUserId(), profile.getId());
            return true;
        }
        if (clearFingerprint.isPresent()) {
            applyClearAll(callback, profile, clearFingerprint.orElseThrow());
            return true;
        }
        if (conditionId.isPresent()) {
            showNotificationCondition(callback.getChatId(), profile, conditionId.orElseThrow());
            return true;
        }
        if (editTargetId.isPresent()) {
            beginTargetPriceEdit(callback, profile, editTargetId.orElseThrow());
            return true;
        }
        if (cancelConditionId.isPresent()) {
            pendingTargetPriceStore.remove(
                    callback.getTelegramUserId(),
                    callback.getChatId()
            );
            showNotificationCondition(
                    callback.getChatId(),
                    profile,
                    cancelConditionId.orElseThrow()
            );
            return true;
        }
        if (setAnyId.isPresent()) {
            changeToAnyDecrease(callback.getChatId(), profile, setAnyId.orElseThrow());
            return true;
        }
        if (requestedPage.isPresent() || itemId.isPresent() || removeId.isPresent()) {
            List<TrackedSubscriptionItem> items =
                    subscriptionService.findActive(profile.getId());
            if (requestedPage.isPresent()) {
                showPage(callback.getChatId(), items, requestedPage.getAsInt());
                return true;
            }
            UUID requestedItemId = itemId.orElseGet(removeId::orElseThrow);
            if (removeId.isPresent()) {
                showRemovalConfirmation(callback.getChatId(), items, requestedItemId);
                return true;
            }
            showItemDetails(callback.getChatId(), profile, items, requestedItemId);
            return true;
        }
        SubscriptionEndResult result = subscriptionService.end(
                profile.getId(),
                confirmRemoveId.orElseThrow(),
                clock.instant()
        );
        telegramGateway.sendMessage(removalMessage(callback.getChatId(), result));
        return true;
    }

    private void showNotificationCondition(
            long chatId,
            UserProfile profile,
            UUID subscriptionId
    ) {
        findTrackedItem(profile.getId(), subscriptionId)
                .ifPresentOrElse(
                        item -> telegramGateway.sendMessage(
                                messageFactory.createNotificationCondition(chatId, item)
                        ),
                        () -> telegramGateway.sendMessage(itemNotFoundMessage(chatId))
                );
    }

    private void beginTargetPriceEdit(
            IncomingTelegramCallback callback,
            UserProfile profile,
            UUID subscriptionId
    ) {
        if (findTrackedItem(profile.getId(), subscriptionId).isEmpty()) {
            telegramGateway.sendMessage(itemNotFoundMessage(callback.getChatId()));
            return;
        }
        Instant now = clock.instant();
        pendingTargetPriceStore.put(new PendingTargetPrice(
                callback.getTelegramUserId(),
                callback.getChatId(),
                PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION,
                subscriptionId,
                now.plus(TARGET_INPUT_TTL)
        ), now);
        telegramGateway.sendMessage(messageFactory.createTargetPriceInput(
                callback.getChatId(),
                subscriptionId
        ));
    }

    private void changeToAnyDecrease(
            long chatId,
            UserProfile profile,
            UUID subscriptionId
    ) {
        SubscriptionConditionChangeResult result = subscriptionService.changeToAnyDecrease(
                profile.getId(),
                subscriptionId,
                clock.instant()
        );
        if (result.getStatus() == SubscriptionConditionChangeResult.Status.NOT_FOUND) {
            telegramGateway.sendMessage(itemNotFoundMessage(chatId));
            return;
        }
        if (result.getStatus() == SubscriptionConditionChangeResult.Status.CONFLICT) {
            telegramGateway.sendMessage(conditionConflictMessage(chatId, subscriptionId));
            return;
        }
        telegramGateway.sendMessage(messageFactory.createConditionChanged(
                chatId,
                subscriptionId,
                result.getSubscription().orElseThrow().getNotificationMode(),
                result.getSubscription().orElseThrow().getTargetPrice(),
                result.getStatus() == SubscriptionConditionChangeResult.Status.CHANGED
        ));
    }

    private Optional<TrackedSubscriptionItem> findTrackedItem(UUID userId, UUID subscriptionId) {
        return subscriptionService.findActive(userId).stream()
                .filter(item -> item.getSubscriptionId().equals(subscriptionId))
                .findFirst();
    }

    private OutgoingTelegramMessage conditionConflictMessage(long chatId, UUID subscriptionId) {
        return new OutgoingTelegramMessage(
                chatId,
                "Условие уведомлений уже изменилось. Откройте его ещё раз.",
                List.of(List.of(new TelegramInlineButton(
                        "Открыть условие",
                        SubscriptionCallbackData.encode(
                                SubscriptionCallbackData.Action.SHOW_NOTIFICATION_CONDITION,
                                subscriptionId
                        )
                )))
        );
    }

    private void showRemovalConfirmation(
            long chatId,
            List<TrackedSubscriptionItem> items,
            UUID subscriptionId
    ) {
        for (TrackedSubscriptionItem item : items) {
            if (item.getSubscriptionId().equals(subscriptionId)) {
                telegramGateway.sendMessage(messageFactory.createRemovalConfirmation(chatId, item));
                return;
            }
        }
        telegramGateway.sendMessage(itemNotFoundMessage(chatId));
    }

    private void showPage(
            long chatId,
            List<TrackedSubscriptionItem> items,
            int requestedPage
    ) {
        int pageNumber = normalizedPage(requestedPage, items.size());
        telegramGateway.sendMessage(messageFactory.createList(
                chatId,
                items,
                pageNumber
        ));
    }

    private void showItemDetails(
            long chatId,
            UserProfile profile,
            List<TrackedSubscriptionItem> items,
            UUID subscriptionId
    ) {
        for (int index = 0; index < items.size(); index++) {
            TrackedSubscriptionItem item = items.get(index);
            if (item.getSubscriptionId().equals(subscriptionId)) {
                telegramGateway.sendMessage(messageFactory.createDetails(
                        chatId,
                        item,
                        index + 1,
                        index / TrackedItemsMessageFactory.ITEMS_PER_PAGE,
                        profile.getPriceContext().getCityName(),
                        profile.getPricePreferences()
                ));
                return;
            }
        }
        telegramGateway.sendMessage(itemNotFoundMessage(chatId));
    }

    private OutgoingTelegramMessage itemNotFoundMessage(long chatId) {
        return new OutgoingTelegramMessage(
                chatId,
                "Этот товар больше не отслеживается.",
                TelegramNavigationKeyboard.trackedItemsAndHome()
        );
    }

    private void showClearConfirmation(long chatId, long telegramUserId, UUID userId) {
        ClearSubscriptionsPlan plan = subscriptionService.prepareClearAll(userId);
        if (plan.isEmpty()) {
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    chatId, "У вас нет активных отслеживаний.", TelegramNavigationKeyboard.mainMenu()
            ));
            return;
        }
        telegramGateway.sendMessage(clearConfirmation(chatId, telegramUserId, plan));
    }

    private void applyClearAll(
            IncomingTelegramCallback callback,
            UserProfile profile,
            String fingerprint
    ) {
        ClearSubscriptionsResult result = subscriptionService.clearAll(
                profile.getId(), fingerprint, clock.instant()
        );
        if (result.getStatus() == ClearSubscriptionsResult.Status.PLAN_CHANGED) {
            showClearConfirmation(callback.getChatId(), callback.getTelegramUserId(), profile.getId());
            return;
        }
        String text = result.getStatus() == ClearSubscriptionsResult.Status.CLEARED
                ? "✅ Все отслеживания остановлены.\n\nТеперь можно выбрать новый город."
                : "Активных отслеживаний уже нет.";
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                callback.getChatId(), text, List.of(List.of(
                        TelegramNavigationKeyboard.button(
                                "🌍 Выбрать город", MainMenuCallbackData.Action.REGION
                        ),
                        TelegramNavigationKeyboard.button(
                                "Главное меню", MainMenuCallbackData.Action.HOME
                        )
                ))
        ));
    }

    private OutgoingTelegramMessage clearConfirmation(
            long chatId,
            long telegramUserId,
            ClearSubscriptionsPlan plan
    ) {
        int count = plan.getSubscriptionCount();
        String text = "⚠️ Остановить все отслеживания?\n\nСейчас отслеживается "
                + itemCount(count) + ".\n\nВсе текущие подписки будут завершены.\n\n"
                + "Некоторые товары могли отслеживаться долгое время.\n"
                + "При повторном добавлении начнётся новый период отслеживания и статистики.";
        return new OutgoingTelegramMessage(chatId, text, List.of(
                List.of(new TelegramInlineButton(
                        "Да, очистить все",
                        clearCallbackCodec.encodeConfirm(plan.getFingerprint(), telegramUserId)
                )),
                List.of(TelegramNavigationKeyboard.button(
                        "← Назад", MainMenuCallbackData.Action.TRACKED_ITEMS
                ))
        ));
    }

    private String itemCount(int count) {
        int lastTwo = Math.abs(count) % 100;
        int last = lastTwo % 10;
        String word = lastTwo >= 11 && lastTwo <= 14 ? "товаров"
                : last == 1 ? "товар"
                : last >= 2 && last <= 4 ? "товара" : "товаров";
        return count + " " + word;
    }

    private int normalizedPage(int requestedPage, int itemCount) {
        if (itemCount == 0) {
            return 0;
        }
        int lastPage = (itemCount - 1) / TrackedItemsMessageFactory.ITEMS_PER_PAGE;
        return Math.min(requestedPage, lastPage);
    }

    private OutgoingTelegramMessage removalMessage(
            long chatId,
            SubscriptionEndResult result
    ) {
        String text = switch (result.getStatus()) {
            case ENDED -> "Отслеживание остановлено. Уведомления по этому товару "
                    + "больше не придут.";
            case NOT_FOUND -> "Этот товар уже удалён из списка отслеживания.";
        };
        return new OutgoingTelegramMessage(
                chatId,
                text,
                TelegramNavigationKeyboard.trackedItemsAndHome()
        );
    }

    private boolean isTrackedCommand(String text) {
        return text.equals("/tracked") || text.startsWith("/tracked@");
    }

}
