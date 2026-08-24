package com.priceradar.telegram.application;

import com.priceradar.tracking.application.SubscriptionEndResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public class TrackedItemsMessageHandler {

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
        Optional<UUID> removeId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.REMOVE,
                callback.getData()
        );
        Optional<UUID> itemId = SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.OPEN_ITEM,
                callback.getData()
        );
        OptionalInt requestedPage = TrackedItemsPageCallbackData.parse(callback.getData());
        if (removeId.isEmpty() && itemId.isEmpty() && requestedPage.isEmpty()) {
            return false;
        }

        if (!callback.isPrivateChat()) {
            return true;
        }
        UserProfile profile = userProfileService.getOrCreate(
                callback.getTelegramUserId(),
                callback.getChatId()
        );
        if (requestedPage.isPresent() || itemId.isPresent()) {
            List<TrackedSubscriptionItem> items =
                    subscriptionService.findActive(profile.getId());
            if (requestedPage.isPresent()) {
                showPage(callback.getChatId(), items, requestedPage.getAsInt());
                return true;
            }
            showItemDetails(callback.getChatId(), profile, items, itemId.orElseThrow());
            return true;
        }
        SubscriptionEndResult result = subscriptionService.end(
                profile.getId(),
                removeId.orElseThrow(),
                clock.instant()
        );
        telegramGateway.sendMessage(removalMessage(callback.getChatId(), result));
        return true;
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
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                chatId,
                "Этот товар больше не отслеживается.",
                TelegramNavigationKeyboard.trackedItemsAndHome()
        ));
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
