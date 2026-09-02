package com.priceradar.telegram.application;

import com.priceradar.product.application.InvalidProductUrlException;
import com.priceradar.product.application.BatchResolvedQuoteResult;
import com.priceradar.product.application.BatchResolvedQuoteItem;
import com.priceradar.product.application.ResolvedQuote;
import com.priceradar.product.application.ResolvedQuoteBatchService;
import com.priceradar.product.application.ResolvedQuoteResult;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.SubscriptionCreationResult;
import com.priceradar.tracking.application.SubscriptionConditionChangeResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TelegramCurrentQuoteHandler {

    private static final String PROCESSING_MESSAGE = "⏳ Ваш запрос обрабатывается...";
    private final WildberriesLinkExtractor linkExtractor;
    private final UserProfileService userProfileService;
    private final ResolvedQuoteService resolvedQuoteService;
    private final ResolvedQuoteBatchService batchService;
    private final TelegramQuoteMessageFactory messageFactory;
    private final TelegramMultiProductQuoteMessageFactory multiProductMessageFactory;
    private final MultiProductQuoteCallbackCodec multiProductCallbackCodec;
    private final MultiProductQuoteSessionStore sessionStore;
    private final SubscriptionService subscriptionService;
    private final TelegramGateway telegramGateway;
    private final Clock clock;
    private final Duration pendingActionTtl;
    private final int maxProductLinksPerMessage;

    public TelegramCurrentQuoteHandler(
            WildberriesLinkExtractor linkExtractor,
            UserProfileService userProfileService,
            ResolvedQuoteService resolvedQuoteService,
            ResolvedQuoteBatchService batchService,
            TelegramQuoteMessageFactory messageFactory,
            TelegramMultiProductQuoteMessageFactory multiProductMessageFactory,
            MultiProductQuoteCallbackCodec multiProductCallbackCodec,
            MultiProductQuoteSessionStore sessionStore,
            SubscriptionService subscriptionService,
            TelegramGateway telegramGateway,
            Clock clock,
            Duration pendingActionTtl,
            int maxProductLinksPerMessage
    ) {
        if (linkExtractor == null || userProfileService == null || resolvedQuoteService == null || batchService == null
                || messageFactory == null || multiProductMessageFactory == null || multiProductCallbackCodec == null
                || sessionStore == null || subscriptionService == null || telegramGateway == null || clock == null
                || pendingActionTtl == null) {
            throw new IllegalArgumentException("Telegram quote handler dependencies must not be null");
        }
        if (pendingActionTtl.isZero() || pendingActionTtl.isNegative() || maxProductLinksPerMessage <= 0) {
            throw new IllegalArgumentException("Telegram quote policy values must be positive");
        }
        this.linkExtractor = linkExtractor;
        this.userProfileService = userProfileService;
        this.resolvedQuoteService = resolvedQuoteService;
        this.batchService = batchService;
        this.messageFactory = messageFactory;
        this.multiProductMessageFactory = multiProductMessageFactory;
        this.multiProductCallbackCodec = multiProductCallbackCodec;
        this.sessionStore = sessionStore;
        this.subscriptionService = subscriptionService;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
        this.pendingActionTtl = pendingActionTtl;
        this.maxProductLinksPerMessage = maxProductLinksPerMessage;
    }

    public boolean handle(IncomingTelegramMessage message) {
        if (!message.isPrivateChat()) {
            return false;
        }

        WildberriesLinks links = linkExtractor.extract(message.getText());
        if (links.getSharedBasketUrl().isPresent() || links.getProductLinks().isEmpty()) {
            return false;
        }

        telegramGateway.sendMessage(OutgoingTelegramMessage.text(
                message.getChatId(), PROCESSING_MESSAGE
        ));
        UserProfile profile = userProfileService.getOrCreate(
                message.getTelegramUserId(),
                message.getChatId()
        );
        if (links.getProductLinks().size() == 1) {
            WildberriesProductLink productLink = links.getProductLinks().getFirst();
            telegramGateway.sendMessage(resolveQuote(message.getChatId(), productLink.getUrl(), profile));
            sendExtractionWarning(message.getChatId(), links);
            return true;
        }
        handleMultipleProducts(message, links, profile);
        return true;
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        if (!callback.isPrivateChat()) return false;
        Optional<MultiProductQuoteCallbackCodec.Decoded> decoded = multiProductCallbackCodec.decode(
                callback.getData(), callback.getTelegramUserId()
        );
        if (decoded.isEmpty()) return false;

        UserProfile profile = userProfileService.getOrCreate(
                callback.getTelegramUserId(), callback.getChatId()
        );
        Optional<MultiProductQuoteSession> stored = sessionStore.findOwned(
                decoded.orElseThrow().getSessionId(), profile.getId()
        );
        if (stored.isEmpty() || stored.orElseThrow().isExpired(clock.instant())) {
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    callback.getChatId(),
                    "Этот список устарел. Отправьте ссылки на товары ещё раз.",
                    TelegramNavigationKeyboard.addProductAndHome()
            ));
            return true;
        }
        MultiProductQuoteSession session = stored.orElseThrow();
        if (decoded.orElseThrow().getAction() == MultiProductQuoteCallbackCodec.Action.ALL_MINIMUM) {
            trackAllAtMinimum(callback, profile, session);
            return true;
        }
        if (decoded.orElseThrow().getAction() == MultiProductQuoteCallbackCodec.Action.PAGE) {
            telegramGateway.sendMessage(multiProductMessageFactory.createPage(
                    callback.getChatId(), callback.getTelegramUserId(), session,
                    decoded.orElseThrow().getValue(), false, 0
            ));
            return true;
        }
        int position = decoded.orElseThrow().getValue();
        if (position >= session.getItems().size()) {
            return false;
        }
        Optional<ResolvedQuote> quote = session.getItems().get(position).getQuote();
        if (quote.isEmpty() || !clock.instant().isBefore(quote.orElseThrow().getExpiresAt())) {
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    callback.getChatId(),
                    "Данные товара устарели. Отправьте ссылку на него ещё раз.",
                    TelegramNavigationKeyboard.addProductAndHome()
            ));
            return true;
        }
        telegramGateway.sendMessage(messageFactory.createQuoteMessage(
                callback.getChatId(), quote.orElseThrow(), profile
        ));
        return true;
    }

    private void trackAllAtMinimum(
            IncomingTelegramCallback callback,
            UserProfile profile,
            MultiProductQuoteSession session
    ) {
        Instant now = clock.instant();
        int created = 0;
        int updated = 0;
        int alreadyTracked = 0;
        int skipped = 0;
        for (MultiProductQuoteItem item : session.getItems()) {
            Optional<ResolvedQuote> quote = item.getQuote();
            if (quote.isEmpty() || !now.isBefore(quote.orElseThrow().getExpiresAt())) {
                skipped++;
                continue;
            }
            SubscriptionCreationResult result = subscriptionService.createFromQuote(
                    profile.getId(), quote.orElseThrow().getQuoteSnapshotId(),
                    NotificationMode.ANY_DECREASE, Optional.empty(), now
            );
            if (result.getStatus() == SubscriptionCreationResult.Status.CREATED) {
                created++;
            } else if (result.getStatus() == SubscriptionCreationResult.Status.ALREADY_ACTIVE) {
                if (result.getSubscription().orElseThrow().getNotificationMode() == NotificationMode.ANY_DECREASE) {
                    alreadyTracked++;
                } else {
                    SubscriptionConditionChangeResult change = subscriptionService.changeToAnyDecrease(
                            profile.getId(), result.getSubscription().orElseThrow().getId(), now
                    );
                    if (change.getStatus() == SubscriptionConditionChangeResult.Status.CHANGED) {
                        updated++;
                    } else if (change.getStatus() == SubscriptionConditionChangeResult.Status.UNCHANGED) {
                        alreadyTracked++;
                    } else {
                        skipped++;
                    }
                }
            } else {
                skipped++;
            }
        }

        StringBuilder text = new StringBuilder("🔔 Отслеживание настроено");
        if (created > 0) text.append("\n\nДобавлено: ").append(created).append('.');
        if (updated > 0) text.append("\nРежим изменён: ").append(updated).append('.');
        if (alreadyTracked > 0) text.append("\nУже отслеживались: ").append(alreadyTracked).append('.');
        if (skipped > 0) text.append("\nНе удалось добавить: ").append(skipped).append('.');
        if (created == 0 && updated == 0 && alreadyTracked == 0) {
            text.append("\n\nПодходящих товаров для добавления не найдено.");
        } else {
            text.append("\n\nРежим уведомлений: новая минимальная цена.");
        }
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                callback.getChatId(), text.toString(), TelegramNavigationKeyboard.trackedItemsAndHome()
        ));
    }

    private void handleMultipleProducts(
            IncomingTelegramMessage message,
            WildberriesLinks links,
            UserProfile profile
    ) {
        BatchResolvedQuoteResult batch = batchService.resolve(
                links.getProductLinks().stream().map(WildberriesProductLink::getParsedUrl).toList(),
                profile.getPriceContext()
        );
        if (!batch.isSuccess()) {
            telegramGateway.sendMessage(messageFactory.createFailureMessage(
                    message.getChatId(), ResolvedQuoteResult.providerFailure(batch.getFailure().orElseThrow())
            ));
            return;
        }

        List<MultiProductQuoteItem> items = new ArrayList<>();
        List<BatchResolvedQuoteItem> results = batch.getItemResults();
        for (int position = 0; position < results.size(); position++) {
            WildberriesProductLink link = links.getProductLinks().get(position);
            items.add(toSessionItem(position, link, results.get(position)));
        }
        Instant createdAt = clock.instant();
        MultiProductQuoteSession session = new MultiProductQuoteSession(
                UUID.randomUUID(), profile.getId(), createdAt, createdAt.plus(pendingActionTtl), items
        );
        sessionStore.save(session);
        telegramGateway.sendMessage(multiProductMessageFactory.createPage(
                message.getChatId(), message.getTelegramUserId(), session, 0,
                links.isTruncated(), links.getInvalidWildberriesUrlCount()
        ));
    }

    private MultiProductQuoteItem toSessionItem(
            int position,
            WildberriesProductLink link,
            BatchResolvedQuoteItem batchItem
    ) {
        ResolvedQuoteResult result = batchItem.getResult();
        if (!result.isSuccess()) {
            boolean unavailable = result.getFailureCode()
                    .filter(ResolvedQuoteResult.FailureCode.VARIANT_NOT_RESOLVED::equals)
                    .isPresent() && batchItem.getProductTitle().isPresent();
            return new MultiProductQuoteItem(
                    position, link.getParsedUrl().getNmId(),
                    batchItem.getProductTitle().orElse("Товар Wildberries #" + link.getParsedUrl().getNmId()),
                    unavailable ? MultiProductQuoteItem.Status.UNAVAILABLE
                            : MultiProductQuoteItem.Status.UNRESOLVED,
                    Optional.empty()
            );
        }
        ResolvedQuote quote = result.getQuote().orElseThrow();
        String displayName = quote.getTitle().orElse("Товар Wildberries #" + quote.getNmId());
        MultiProductQuoteItem.Status status = quote.getInterpretedPrice().getStatus() == SnapshotStatus.UNAVAILABLE
                ? MultiProductQuoteItem.Status.UNAVAILABLE : MultiProductQuoteItem.Status.AVAILABLE;
        return new MultiProductQuoteItem(
                position, quote.getNmId(), displayName, status, Optional.of(quote)
        );
    }

    private OutgoingTelegramMessage resolveQuote(long chatId, String productUrl, UserProfile profile) {
        try {
            ResolvedQuoteResult result = resolvedQuoteService.resolve(
                    productUrl,
                    profile.getPriceContext()
            );
            if (!result.isSuccess()) {
                return messageFactory.createFailureMessage(chatId, result);
            }
            return messageFactory.createQuoteMessage(
                    chatId,
                    result.getQuote().orElseThrow(),
                    profile
            );
        } catch (InvalidProductUrlException exception) {
            return new OutgoingTelegramMessage(
                    chatId,
                    "Не получилось распознать ссылку. Отправьте ссылку на товар в формате:\n"
                            + "https://www.wildberries.ru/catalog/10302970/detail.aspx",
                    TelegramNavigationKeyboard.addProductAndHome()
            );
        }
    }

    private void sendExtractionWarning(long chatId, WildberriesLinks links) {
        StringBuilder warning = new StringBuilder();
        if (links.isTruncated()) {
            warning.append("⚠️ В одном сообщении можно обработать не более ")
                    .append(maxProductLinksPerMessage)
                    .append(" товаров.\n")
                    .append("Остальные ссылки пропущены.");
        }
        if (links.getInvalidWildberriesUrlCount() > 0) {
            if (!warning.isEmpty()) {
                warning.append("\n\n");
            }
            warning.append("⚠️ Некоторые ссылки Wildberries не удалось распознать.");
        }
        if (!warning.isEmpty()) {
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    chatId, warning.toString(), TelegramNavigationKeyboard.addProductAndHome()
            ));
        }
    }
}
