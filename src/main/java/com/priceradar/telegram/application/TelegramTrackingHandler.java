package com.priceradar.telegram.application;

import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.tracking.application.SubscriptionCreationResult;
import com.priceradar.tracking.application.SubscriptionPreparationResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class TelegramTrackingHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramTrackingHandler.class);
    private static final Duration TARGET_INPUT_TTL = Duration.ofMinutes(15);

    private final UserProfileService userProfileService;
    private final SubscriptionService subscriptionService;
    private final TargetPriceParser targetPriceParser;
    private final TrackingCallbackCodec trackingCallbackCodec;
    private final PendingTargetPriceStore pendingTargetPriceStore;
    private final TelegramGateway telegramGateway;
    private final Clock clock;

    public TelegramTrackingHandler(
            UserProfileService userProfileService,
            SubscriptionService subscriptionService,
            TargetPriceParser targetPriceParser,
            TrackingCallbackCodec trackingCallbackCodec,
            PendingTargetPriceStore pendingTargetPriceStore,
            TelegramGateway telegramGateway,
            Clock clock
    ) {
        if (userProfileService == null || subscriptionService == null || targetPriceParser == null
                || trackingCallbackCodec == null || pendingTargetPriceStore == null
                || telegramGateway == null || clock == null) {
            throw new IllegalArgumentException("Telegram tracking handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.subscriptionService = subscriptionService;
        this.targetPriceParser = targetPriceParser;
        this.trackingCallbackCodec = trackingCallbackCodec;
        this.pendingTargetPriceStore = pendingTargetPriceStore;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
    }

    public void handleCallback(IncomingTelegramCallback callback) {
        try {
            if (!callback.isPrivateChat()) {
                return;
            }
            Optional<TrackingCallbackData> callbackData = trackingCallbackCodec.decode(
                    callback.getData(),
                    callback.getTelegramUserId()
            );
            if (callbackData.isEmpty()) {
                telegramGateway.sendMessage(OutgoingTelegramMessage.text(
                        callback.getChatId(),
                        "Эта кнопка устарела или пока не поддерживается. Отправьте ссылку товара заново."
                ));
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
        } finally {
            answerCallbackBestEffort(callback.getCallbackQueryId());
        }
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
            telegramGateway.sendMessage(expiredQuoteMessage(message.getChatId()));
            return true;
        }
        if (message.getText().startsWith("http://") || message.getText().startsWith("https://")) {
            pendingTargetPriceStore.remove(pending.get());
            return false;
        }

        Optional<RubleAmount> targetPrice = targetPriceParser.parse(message.getText());
        if (targetPrice.isEmpty()) {
            telegramGateway.sendMessage(OutgoingTelegramMessage.text(
                    message.getChatId(),
                    "Введите положительную цену в рублях, например 1500 или 1499,90."
            ));
            return true;
        }

        UserProfile profile = userProfileService.getOrCreate(
                message.getTelegramUserId(),
                message.getChatId()
        );
        SubscriptionCreationResult result = subscriptionService.createFromQuote(
                profile.getId(),
                pending.get().getQuoteSnapshotId(),
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

        pendingTargetPriceStore.put(new PendingTargetPrice(
                callback.getTelegramUserId(),
                callback.getChatId(),
                callbackData.getQuoteSnapshotId(),
                now.plus(TARGET_INPUT_TTL)
        ), now);
        telegramGateway.sendMessage(OutgoingTelegramMessage.text(
                callback.getChatId(),
                "Введите целевую цену в рублях, например 1500 или 1499,90. "
                        + "Ответ действителен 15 минут."
        ));
    }

    private OutgoingTelegramMessage creationMessage(
            long chatId,
            SubscriptionCreationResult result,
            UserProfile profile,
            Optional<RubleAmount> targetPrice
    ) {
        String region = profile.getPriceContext().getCityName();
        return switch (result.getStatus()) {
            case CREATED -> {
                if (result.isTargetAlreadyReached()) {
                    yield OutgoingTelegramMessage.text(
                            chatId,
                            "Отслеживание включено. Целевая цена "
                                    + format(targetPrice.orElseThrow())
                                    + " уже достигнута по текущему "
                                    + "приблизительному наблюдению. Повтор без предварительного "
                                    + "роста цены отправлен не будет.\nРегион: " + region
                                    + "\n⚠️ Цена Wildberries приблизительная."
                    );
                }
                String mode = targetPrice.isPresent()
                        ? "целевая цена " + format(targetPrice.orElseThrow())
                        : "любое снижение обычной цены";
                yield OutgoingTelegramMessage.text(
                        chatId,
                        "Отслеживание включено. Режим: " + mode
                                + ". Выбранный вариант товара зафиксирован.\nРегион: " + region
                );
            }
            case ALREADY_ACTIVE -> OutgoingTelegramMessage.text(
                    chatId,
                    "Этот вариант товара уже отслеживается. Новая подписка не создана.\nРегион: "
                            + region
            );
            case LIMIT_REACHED -> OutgoingTelegramMessage.text(
                    chatId,
                    "Достигнут лимит: 50 активных подписок. Удалите один из отслеживаемых товаров."
            );
            case QUOTE_EXPIRED -> expiredQuoteMessage(chatId);
            case USER_NOT_FOUND, WATCH_TARGET_NOT_FOUND -> OutgoingTelegramMessage.text(
                    chatId,
                    "Не удалось найти актуальные данные товара. Отправьте ссылку заново."
            );
        };
    }

    private OutgoingTelegramMessage preparationMessage(
            long chatId,
            SubscriptionPreparationResult result,
            UserProfile profile
    ) {
        return switch (result.getStatus()) {
            case ALREADY_ACTIVE -> OutgoingTelegramMessage.text(
                    chatId,
                    "Этот вариант товара уже отслеживается. Новая подписка не создана.\nРегион: "
                            + profile.getPriceContext().getCityName()
            );
            case LIMIT_REACHED -> OutgoingTelegramMessage.text(
                    chatId,
                    "Достигнут лимит: 50 активных подписок. Удалите один из отслеживаемых товаров."
            );
            case QUOTE_EXPIRED -> expiredQuoteMessage(chatId);
            case USER_NOT_FOUND, WATCH_TARGET_NOT_FOUND -> OutgoingTelegramMessage.text(
                    chatId,
                    "Не удалось найти актуальные данные товара. Отправьте ссылку заново."
            );
            case READY -> throw new IllegalArgumentException("READY preparation does not need a message");
        };
    }

    private OutgoingTelegramMessage expiredQuoteMessage(long chatId) {
        return OutgoingTelegramMessage.text(
                chatId,
                "Карточка цены устарела. Отправьте ссылку Wildberries заново. "
                        + "Автоматическое обновление из этой кнопки не выполняется."
        );
    }

    private String format(RubleAmount amount) {
        return RublePriceFormatter.format(amount);
    }

    private void answerCallbackBestEffort(String callbackQueryId) {
        try {
            telegramGateway.answerCallbackQuery(callbackQueryId);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not acknowledge Telegram callback, errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
