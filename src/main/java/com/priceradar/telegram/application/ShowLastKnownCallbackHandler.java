package com.priceradar.telegram.application;

import com.priceradar.tracking.application.LatestSnapshotQueryService;
import com.priceradar.tracking.application.LatestSnapshotView;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ShowLastKnownCallbackHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShowLastKnownCallbackHandler.class);

    private final UserProfileService userProfileService;
    private final LatestSnapshotQueryService queryService;
    private final LatestSnapshotMessageFactory messageFactory;
    private final TelegramGateway telegramGateway;

    public ShowLastKnownCallbackHandler(
            UserProfileService userProfileService,
            LatestSnapshotQueryService queryService,
            LatestSnapshotMessageFactory messageFactory,
            TelegramGateway telegramGateway
    ) {
        if (userProfileService == null || queryService == null || messageFactory == null
                || telegramGateway == null) {
            throw new IllegalArgumentException("show last known handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.queryService = queryService;
        this.messageFactory = messageFactory;
        this.telegramGateway = telegramGateway;
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        Optional<ShowLastKnownCallbackData> callbackData = ShowLastKnownCallbackData.parse(
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
            Optional<LatestSnapshotView> snapshot = queryService.findLatest(
                    profile.getId(),
                    callbackData.get().getSubscriptionId()
            );
            OutgoingTelegramMessage message = snapshot
                    .map(value -> messageFactory.create(callback.getChatId(), value, profile))
                    .orElseGet(() -> notFoundMessage(callback.getChatId()));
            telegramGateway.sendMessage(message);
            return true;
        } finally {
            answerCallbackBestEffort(callback.getCallbackQueryId());
        }
    }

    private OutgoingTelegramMessage notFoundMessage(long chatId) {
        return OutgoingTelegramMessage.text(
                chatId,
                "Активная подписка не найдена или уже удалена. Новый запрос к Wildberries "
                        + "не выполнялся.\n\nОбновить список: /tracked"
        );
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
