package com.priceradar.telegram.application;

import com.priceradar.tracking.application.SubscriptionEndResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class TrackedItemsMessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackedItemsMessageHandler.class);

    private final UserProfileService userProfileService;
    private final SubscriptionService subscriptionService;
    private final TrackedItemsMessageFactory messageFactory;
    private final TelegramGateway telegramGateway;
    private final Clock clock;

    public TrackedItemsMessageHandler(
            UserProfileService userProfileService,
            SubscriptionService subscriptionService,
            TrackedItemsMessageFactory messageFactory,
            TelegramGateway telegramGateway,
            Clock clock
    ) {
        if (userProfileService == null || subscriptionService == null || messageFactory == null
                || telegramGateway == null || clock == null) {
            throw new IllegalArgumentException("tracked items handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.subscriptionService = subscriptionService;
        this.messageFactory = messageFactory;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
    }

    public boolean handleMessage(IncomingTelegramMessage message) {
        if (!message.isPrivateChat() || !isTrackedCommand(message.getText())) {
            return false;
        }
        UserProfile profile = userProfileService.getOrCreate(
                message.getTelegramUserId(),
                message.getChatId()
        );
        List<TrackedSubscriptionItem> items = subscriptionService.findActive(profile.getId());
        messageFactory.create(
                message.getChatId(),
                items,
                profile.getPriceContext().getCityName()
        ).forEach(telegramGateway::sendMessage);
        return true;
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        Optional<RemoveTrackingCallbackData> callbackData = RemoveTrackingCallbackData.parse(
                callback.getData()
        );
        if (callbackData.isEmpty()) {
            return false;
        }

        try {
            if (!callback.isPrivateChat()) {
                return true;
            }
            UserProfile profile = userProfileService.getOrCreate(
                    callback.getTelegramUserId(),
                    callback.getChatId()
            );
            SubscriptionEndResult result = subscriptionService.end(
                    profile.getId(),
                    callbackData.get().getSubscriptionId(),
                    clock.instant()
            );
            telegramGateway.sendMessage(removalMessage(callback.getChatId(), result));
            return true;
        } finally {
            answerCallbackBestEffort(callback.getCallbackQueryId());
        }
    }

    private OutgoingTelegramMessage removalMessage(
            long chatId,
            SubscriptionEndResult result
    ) {
        return switch (result.getStatus()) {
            case ENDED -> OutgoingTelegramMessage.text(
                    chatId,
                    "Отслеживание остановлено. Текущий период подписки завершён, дальнейшие "
                            + "уведомления по нему отправляться не будут. Если добавить этот товар "
                            + "снова, начнётся новый период отслеживания.\n\nОткрыть список: /tracked"
            );
            case NOT_FOUND -> OutgoingTelegramMessage.text(
                    chatId,
                    "Активная подписка не найдена или уже удалена."
                            + "\n\nОбновить список: /tracked"
            );
        };
    }

    private boolean isTrackedCommand(String text) {
        return text.equals("/tracked") || text.startsWith("/tracked@");
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
