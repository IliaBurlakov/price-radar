package com.priceradar.telegram.application;

import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.tracking.application.SubscriptionCreationResult;
import com.priceradar.tracking.application.SubscriptionConditionChangeResult;
import com.priceradar.tracking.application.SubscriptionPreparationResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TelegramTrackingHandler {

    private final UserProfileService userProfileService;
    private final SubscriptionService subscriptionService;
    private final TargetPriceParser targetPriceParser;
    private final TrackingCallbackCodec trackingCallbackCodec;
    private final PendingTargetPriceStore pendingTargetPriceStore;
    private final TelegramGateway telegramGateway;
    private final Clock clock;
    private final Duration pendingActionTtl;
    private final int activeSubscriptionLimit;

    public TelegramTrackingHandler(
            UserProfileService userProfileService,
            SubscriptionService subscriptionService,
            TargetPriceParser targetPriceParser,
            TrackingCallbackCodec trackingCallbackCodec,
            PendingTargetPriceStore pendingTargetPriceStore,
            TelegramGateway telegramGateway,
            Clock clock,
            Duration pendingActionTtl,
            int activeSubscriptionLimit
    ) {
        if (userProfileService == null || subscriptionService == null || targetPriceParser == null
                || trackingCallbackCodec == null || pendingTargetPriceStore == null
                || telegramGateway == null || clock == null || pendingActionTtl == null) {
            throw new IllegalArgumentException("Telegram tracking handler dependencies must not be null");
        }
        if (pendingActionTtl.isZero() || pendingActionTtl.isNegative() || activeSubscriptionLimit <= 0) {
            throw new IllegalArgumentException("Telegram tracking policy values must be positive");
        }
        this.userProfileService = userProfileService;
        this.subscriptionService = subscriptionService;
        this.targetPriceParser = targetPriceParser;
        this.trackingCallbackCodec = trackingCallbackCodec;
        this.pendingTargetPriceStore = pendingTargetPriceStore;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
        this.pendingActionTtl = pendingActionTtl;
        this.activeSubscriptionLimit = activeSubscriptionLimit;
    }

    public void handleCallback(IncomingTelegramCallback callback) {
        if (!callback.isPrivateChat()) {
            return;
        }
        Optional<TrackingCallbackData> callbackData = trackingCallbackCodec.decode(
                callback.getData(),
                callback.getTelegramUserId()
        );
        if (callbackData.isEmpty()) {
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    callback.getChatId(),
                    "Эта кнопка устарела или пока не поддерживается. Отправьте ссылку товара заново.",
                    TelegramNavigationKeyboard.addProductAndHome()
            ));
            return;
        }

        if (callbackData.get().getAction() == TrackingCallbackData.Action.CANCEL_TARGET) {
            cancelTargetPriceInput(callback, callbackData.orElseThrow());
            return;
        }

        UserProfile profile = userProfileService.getOrCreate(
                callback.getTelegramUserId(),
                callback.getChatId()
        );
        if (callbackData.get().getAction() == TrackingCallbackData.Action.TRACK_ANY_DECREASE) {
            createAnyDecrease(callback, callbackData.get(), profile);
            return;
        }
        beginTargetPriceInput(callback, callbackData.get(), profile);
    }

    public boolean handleTargetPriceInput(IncomingTelegramMessage message) {
        if (!message.isPrivateChat()) {
            return false;
        }
        Instant now = clock.instant();
        Optional<PendingTargetPrice> pending = pendingTargetPriceStore.find(
                message.getTelegramUserId(),
                message.getChatId(),
                now
        );
        if (pending.isEmpty() || message.getText().startsWith("/")) {
            return false;
        }
        if (pending.get().isExpired(now)) {
            pendingTargetPriceStore.remove(pending.get());
            telegramGateway.sendMessage(expiredPendingMessage(
                    message.getChatId(),
                    pending.orElseThrow()
            ));
            return true;
        }
        if (message.getText().startsWith("http://") || message.getText().startsWith("https://")) {
            pendingTargetPriceStore.remove(pending.get());
            return false;
        }

        Optional<RubleAmount> targetPrice = targetPriceParser.parse(message.getText());
        if (targetPrice.isEmpty()) {
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    message.getChatId(),
                    "Введите цену в рублях, например 1500.",
                    targetInputKeyboard(pending.orElseThrow(), message.getTelegramUserId())
            ));
            return true;
        }

        UserProfile profile = userProfileService.getOrCreate(
                message.getTelegramUserId(),
                message.getChatId()
        );
        if (pending.get().getPurpose() == PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION) {
            SubscriptionConditionChangeResult result = subscriptionService.changeToTargetPrice(
                    profile.getId(),
                    pending.get().requireSubscriptionId(),
                    targetPrice.orElseThrow(),
                    now
            );
            telegramGateway.sendMessage(conditionChangeMessage(
                    message.getChatId(),
                    pending.get().requireSubscriptionId(),
                    targetPrice.orElseThrow(),
                    result
            ));
        } else {
            SubscriptionCreationResult result = subscriptionService.createFromQuote(
                    profile.getId(),
                    pending.get().requireQuoteSnapshotId(),
                    NotificationMode.TARGET_PRICE,
                    targetPrice,
                    now
            );
            telegramGateway.sendMessage(creationMessage(
                    message.getChatId(),
                    result,
                    profile,
                    targetPrice
            ));
        }
        pendingTargetPriceStore.remove(pending.get());
        return true;
    }

    private void createAnyDecrease(
            IncomingTelegramCallback callback,
            TrackingCallbackData callbackData,
            UserProfile profile
    ) {
        SubscriptionCreationResult result = subscriptionService.createFromQuote(
                profile.getId(),
                callbackData.getQuoteSnapshotId(),
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                clock.instant()
        );
        telegramGateway.sendMessage(creationMessage(
                callback.getChatId(),
                result,
                profile,
                Optional.empty()
        ));
    }

    private void beginTargetPriceInput(
            IncomingTelegramCallback callback,
            TrackingCallbackData callbackData,
            UserProfile profile
    ) {
        Instant now = clock.instant();
        SubscriptionPreparationResult preparation = subscriptionService.prepareFromQuote(
                profile.getId(),
                callbackData.getQuoteSnapshotId(),
                now
        );
        if (!preparation.isReady()) {
            telegramGateway.sendMessage(preparationMessage(
                    callback.getChatId(),
                    preparation,
                    profile
            ));
            return;
        }

        PendingTargetPrice pending = new PendingTargetPrice(
                callback.getTelegramUserId(),
                callback.getChatId(),
                PendingTargetPrice.Purpose.CREATE_SUBSCRIPTION,
                callbackData.getQuoteSnapshotId(),
                now.plus(pendingActionTtl)
        );
        pendingTargetPriceStore.put(pending, now);
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                callback.getChatId(),
                "Введите желаемую цену в рублях, например 1500.\n"
                        + "Ответ можно отправить в течение 15 минут.",
                targetInputKeyboard(pending, callback.getTelegramUserId())
        ));
    }

    private List<List<TelegramInlineButton>> targetInputKeyboard(
            PendingTargetPrice pending,
            long telegramUserId
    ) {
        if (pending.getPurpose() == PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION) {
            return List.of(List.of(new TelegramInlineButton(
                    "← Назад",
                    SubscriptionCallbackData.encode(
                            SubscriptionCallbackData.Action.CANCEL_CONDITION_EDIT,
                            pending.requireSubscriptionId()
                    )
            )));
        }
        return List.of(List.of(new TelegramInlineButton(
                "Отмена",
                trackingCallbackCodec.encode(
                        TrackingCallbackData.Action.CANCEL_TARGET,
                        pending.requireQuoteSnapshotId(),
                        telegramUserId
                )
        )));
    }

    private void cancelTargetPriceInput(
            IncomingTelegramCallback callback,
            TrackingCallbackData callbackData
    ) {
        Instant now = clock.instant();
        pendingTargetPriceStore.find(
                        callback.getTelegramUserId(),
                        callback.getChatId(),
                        now
                )
                .filter(pending -> pending.getPurpose()
                                == PendingTargetPrice.Purpose.CREATE_SUBSCRIPTION
                        && pending.requireQuoteSnapshotId().equals(
                                callbackData.getQuoteSnapshotId()
                        ))
                .ifPresent(pendingTargetPriceStore::remove);
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                callback.getChatId(),
                "Ввод желаемой цены отменён.",
                TelegramNavigationKeyboard.addProductAndHome()
        ));
    }

    public void clearPendingInput(long telegramUserId, long chatId) {
        pendingTargetPriceStore.remove(telegramUserId, chatId);
    }

    private OutgoingTelegramMessage conditionChangeMessage(
            long chatId,
            UUID subscriptionId,
            RubleAmount targetPrice,
            SubscriptionConditionChangeResult result
    ) {
        String text = switch (result.getStatus()) {
            case CHANGED -> "✅ Условие уведомлений изменено.\n\n🎯 Сообщу, когда цена будет не выше "
                    + format(targetPrice) + ".";
            case UNCHANGED -> "Целевая цена уже установлена на " + format(targetPrice) + ".";
            case NOT_FOUND -> "Этот товар больше не отслеживается.";
            case CONFLICT -> "Условие уведомлений уже изменилось. Откройте товар и повторите попытку.";
        };
        return new OutgoingTelegramMessage(
                chatId,
                text,
                result.getStatus() == SubscriptionConditionChangeResult.Status.NOT_FOUND
                        ? TelegramNavigationKeyboard.trackedItemsAndHome()
                        : List.of(List.of(new TelegramInlineButton(
                                "← К товару",
                                SubscriptionCallbackData.encode(
                                        SubscriptionCallbackData.Action.OPEN_ITEM,
                                        subscriptionId
                                )
                        )))
        );
    }

    private OutgoingTelegramMessage expiredPendingMessage(
            long chatId,
            PendingTargetPrice pending
    ) {
        if (pending.getPurpose() == PendingTargetPrice.Purpose.CREATE_SUBSCRIPTION) {
            return expiredQuoteMessage(chatId);
        }
        return new OutgoingTelegramMessage(
                chatId,
                "Время ввода цены истекло. Откройте товар и попробуйте ещё раз.",
                TelegramNavigationKeyboard.trackedItemsAndHome()
        );
    }

    private OutgoingTelegramMessage creationMessage(
            long chatId,
            SubscriptionCreationResult result,
            UserProfile profile,
            Optional<RubleAmount> targetPrice
    ) {
        String region = TelegramDisplayFormatter.region(
                profile.getPriceContext().getCityName()
        );
        return switch (result.getStatus()) {
            case CREATED -> {
                String mode = targetPrice.isPresent()
                        ? "уведомить при цене " + format(targetPrice.orElseThrow())
                        : "уведомлять о новой минимальной цене";
                yield withTrackedButton(
                        chatId,
                        "Отслеживание включено.\n\nРежим: " + mode
                                + "\nГород: " + region
                );
            }
            case ALREADY_ACTIVE -> withTrackedButton(
                    chatId,
                    "Этот товар уже отслеживается.\nГород: " + region
            );
            case LIMIT_REACHED -> withTrackedButton(
                    chatId,
                    "Можно отслеживать не больше " + activeSubscriptionLimit + " товаров. "
                            + "Удалите один из списка, чтобы добавить новый."
            );
            case QUOTE_EXPIRED -> expiredQuoteMessage(chatId);
            case REGION_MISMATCH -> previousRegionQuoteMessage(chatId);
            case USER_NOT_FOUND, WATCH_TARGET_NOT_FOUND -> withAddButton(
                    chatId,
                    "Данные товара устарели. Отправьте ссылку ещё раз."
            );
        };
    }

    private OutgoingTelegramMessage preparationMessage(
            long chatId,
            SubscriptionPreparationResult result,
            UserProfile profile
    ) {
        return switch (result.getStatus()) {
            case ALREADY_ACTIVE -> withTrackedButton(
                    chatId,
                    "Этот товар уже отслеживается.\nГород: "
                            + TelegramDisplayFormatter.region(
                                    profile.getPriceContext().getCityName()
                            )
            );
            case LIMIT_REACHED -> withTrackedButton(
                    chatId,
                    "Можно отслеживать не больше " + activeSubscriptionLimit + " товаров. "
                            + "Удалите один из списка, чтобы добавить новый."
            );
            case QUOTE_EXPIRED -> expiredQuoteMessage(chatId);
            case REGION_MISMATCH -> previousRegionQuoteMessage(chatId);
            case USER_NOT_FOUND, WATCH_TARGET_NOT_FOUND -> withAddButton(
                    chatId,
                    "Данные товара устарели. Отправьте ссылку ещё раз."
            );
            case READY -> throw new IllegalArgumentException("READY preparation does not need a message");
        };
    }

    private OutgoingTelegramMessage expiredQuoteMessage(long chatId) {
        return withAddButton(
                chatId,
                "Эта карточка цены устарела. Отправьте ссылку на товар ещё раз."
        );
    }

    private OutgoingTelegramMessage previousRegionQuoteMessage(long chatId) {
        return withAddButton(
                chatId,
                "Этот результат относится к другому городу. "
                        + "Отправьте ссылку на товар ещё раз."
        );
    }

    private OutgoingTelegramMessage withTrackedButton(long chatId, String text) {
        return withMenuButton(
                chatId,
                text,
                "Мои товары",
                MainMenuCallbackData.Action.TRACKED_ITEMS
        );
    }

    private OutgoingTelegramMessage withAddButton(long chatId, String text) {
        return withMenuButton(
                chatId,
                text,
                "Добавить товар",
                MainMenuCallbackData.Action.ADD_PRODUCT
        );
    }

    private OutgoingTelegramMessage withMenuButton(
            long chatId,
            String text,
            String buttonText,
            MainMenuCallbackData.Action action
    ) {
        return new OutgoingTelegramMessage(
                chatId,
                text,
                List.of(List.of(
                        TelegramNavigationKeyboard.button(buttonText, action),
                        TelegramNavigationKeyboard.button(
                                "Главное меню",
                                MainMenuCallbackData.Action.HOME
                        )
                ))
        );
    }

    private String format(RubleAmount amount) {
        return RublePriceFormatter.format(amount);
    }

}
