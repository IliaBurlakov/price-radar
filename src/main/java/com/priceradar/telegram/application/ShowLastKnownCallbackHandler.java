package com.priceradar.telegram.application;

import com.priceradar.tracking.application.LatestSnapshotQueryService;
import com.priceradar.tracking.application.LatestSnapshotView;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import java.util.Optional;
import java.util.UUID;

public class ShowLastKnownCallbackHandler {

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
        Optional<UUID> subscriptionId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.SHOW_LAST_KNOWN,
                callback.getData()
        );
        if (subscriptionId.isEmpty()) {
            return false;
        }

        if (!callback.isPrivateChat()) {
            return true;
        }
        UserProfile profile = userProfileService.getOrCreate(
                callback.getTelegramUserId(),
                callback.getChatId()
        );
        Optional<LatestSnapshotView> snapshot = queryService.findLatest(
                profile.getId(),
                subscriptionId.orElseThrow()
        );
        OutgoingTelegramMessage message = snapshot
                .map(value -> messageFactory.create(callback.getChatId(), value, profile))
                .orElseGet(() -> notFoundMessage(callback.getChatId()));
        telegramGateway.sendMessage(message);
        return true;
    }

    private OutgoingTelegramMessage notFoundMessage(long chatId) {
        return new OutgoingTelegramMessage(
                chatId,
                "Этот товар больше не отслеживается. Откройте раздел «Мои товары».",
                TelegramNavigationKeyboard.trackedItemsAndHome()
        );
    }

}
