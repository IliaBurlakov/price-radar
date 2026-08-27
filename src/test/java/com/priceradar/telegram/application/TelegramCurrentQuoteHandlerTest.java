package com.priceradar.telegram.application;

import com.priceradar.marketplace.application.MarketplaceProviderFailure;
import com.priceradar.marketplace.application.MarketplaceProviderFailureCode;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.application.BatchResolvedQuoteResult;
import com.priceradar.product.application.BatchResolvedQuoteItem;
import com.priceradar.product.application.ParsedProductUrl;
import com.priceradar.product.application.ProductUrlParser;
import com.priceradar.product.application.ResolvedQuote;
import com.priceradar.product.application.ResolvedQuoteBatchService;
import com.priceradar.product.application.ResolvedQuoteResult;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.sharedbasket.application.SharedBasketUrlParser;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import com.priceradar.tracking.application.SubscriptionCreationResult;
import com.priceradar.tracking.application.SubscriptionConditionChangeResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.domain.Subscription;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramCurrentQuoteHandlerTest {

    private static final long TELEGRAM_ID = 7001L;
    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");
    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void cleanAndSurroundedSingleUrlKeepTheExistingSingleQuoteFlow() {
        TestContext context = context();
        String url = "https://www.wildberries.ru/catalog/389025161/detail.aspx?size=564351571";
        ResolvedQuoteResult failure = ResolvedQuoteResult.failure(
                ResolvedQuoteResult.FailureCode.VARIANT_NOT_RESOLVED, "not resolved"
        );
        when(context.single.resolve(url, context.profile.getPriceContext())).thenReturn(failure);
        when(context.quoteMessages.createFailureMessage(TELEGRAM_ID, failure))
                .thenReturn(OutgoingTelegramMessage.text(TELEGRAM_ID, "failure"));

        assertThat(context.handler.handle(message(url))).isTrue();
        assertThat(context.handler.handle(message("SHEPOT Candles\n" + url + "\nочень нравится"))).isTrue();

        verify(context.single, times(2)).resolve(url, context.profile.getPriceContext());
        verifyNoBatch(context);
    }

    @Test
    void multipleProductsUseOneBatchAndSendOneSummaryInsteadOfOneMessagePerProduct() {
        TestContext context = context();
        List<BatchResolvedQuoteItem> results = List.of(
                item(111, ResolvedQuoteResult.success(quote(111, "Первый", SnapshotStatus.REGULAR_PRICE)), "Первый"),
                item(222, ResolvedQuoteResult.failure(
                        ResolvedQuoteResult.FailureCode.VARIANT_NOT_RESOLVED, "unavailable"
                ), "Недоступный"),
                item(333, ResolvedQuoteResult.success(quote(333, "Третий", SnapshotStatus.REGULAR_PRICE)), "Третий")
        );
        when(context.batch.resolve(any(), eq(context.profile.getPriceContext())))
                .thenReturn(BatchResolvedQuoteResult.success(results));

        context.handler.handle(message("""
                https://www.wildberries.ru/catalog/111/detail.aspx
                https://www.wildberries.ru/catalog/222/detail.aspx
                https://www.wildberries.ru/catalog/333/detail.aspx
                """));

        verify(context.batch).resolve(any(), eq(context.profile.getPriceContext()));
        verify(context.single, never()).resolve(any(), any());
        verify(context.sessions).save(any());
        var sent = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway, times(2)).sendMessage(sent.capture());
        assertThat(sent.getAllValues().getLast().getText()).contains(
                "Найдено товаров: 3", "Первый", "Сейчас недоступен: Недоступный", "Третий"
        );
        assertThat(sent.getAllValues().getLast().getInlineKeyboard())
                .flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .contains("Следить за минимумом для всех");
    }

    @Test
    void globalBatchFailureProducesOneCommonErrorAndNoSession() {
        TestContext context = context();
        MarketplaceProviderFailure failure = new MarketplaceProviderFailure(
                MarketplaceProviderFailureCode.RATE_LIMITED, "rate limited",
                Optional.empty(), "correlation"
        );
        when(context.batch.resolve(any(), eq(context.profile.getPriceContext())))
                .thenReturn(BatchResolvedQuoteResult.failure(failure));
        OutgoingTelegramMessage error = OutgoingTelegramMessage.text(TELEGRAM_ID, "Попробуйте позже");
        when(context.quoteMessages.createFailureMessage(
                eq(TELEGRAM_ID), any(ResolvedQuoteResult.class)
        )).thenReturn(error);

        context.handler.handle(message("""
                https://www.wildberries.ru/catalog/111/detail.aspx
                https://www.wildberries.ru/catalog/222/detail.aspx
                """));

        verify(context.sessions, never()).save(any());
        var sent = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway, times(2)).sendMessage(sent.capture());
        assertThat(sent.getAllValues().getLast()).isEqualTo(error);
    }

    @Test
    void signedItemCallbackOpensTheCorrectPersistedQuote() {
        TestContext context = context();
        ResolvedQuote first = quote(111, "Первый", SnapshotStatus.REGULAR_PRICE);
        ResolvedQuote second = quote(222, "Второй", SnapshotStatus.REGULAR_PRICE);
        MultiProductQuoteSession session = new MultiProductQuoteSession(
                UUID.randomUUID(), context.profile.getId(), NOW, NOW.plusSeconds(900), List.of(
                new MultiProductQuoteItem(0, 111, "Первый", MultiProductQuoteItem.Status.AVAILABLE, Optional.of(first)),
                new MultiProductQuoteItem(1, 222, "Второй", MultiProductQuoteItem.Status.AVAILABLE, Optional.of(second))
        ));
        when(context.sessions.findOwned(session.getId(), context.profile.getId())).thenReturn(Optional.of(session));
        OutgoingTelegramMessage details = OutgoingTelegramMessage.text(TELEGRAM_ID, "Второй");
        when(context.quoteMessages.createQuoteMessage(TELEGRAM_ID, second, context.profile)).thenReturn(details);
        MultiProductQuoteCallbackCodec codec = new MultiProductQuoteCallbackCodec(SECRET);

        boolean handled = context.handler.handleCallback(new IncomingTelegramCallback(
                "callback", TELEGRAM_ID, TELEGRAM_ID, "private",
                codec.encode(MultiProductQuoteCallbackCodec.Action.ITEM, session.getId(), 1, TELEGRAM_ID)
        ));

        assertThat(handled).isTrue();
        verify(context.gateway).sendMessage(details);
    }

    @Test
    void bulkMinimumCallbackCreatesSubscriptionsForEveryResolvedQuote() {
        TestContext context = context();
        ResolvedQuote first = quote(111, "Первый", SnapshotStatus.REGULAR_PRICE);
        ResolvedQuote second = quote(222, "Второй", SnapshotStatus.REGULAR_PRICE);
        MultiProductQuoteSession session = new MultiProductQuoteSession(
                UUID.randomUUID(), context.profile.getId(), NOW, NOW.plusSeconds(900), List.of(
                new MultiProductQuoteItem(0, 111, "Первый", MultiProductQuoteItem.Status.AVAILABLE, Optional.of(first)),
                new MultiProductQuoteItem(1, 222, "Второй", MultiProductQuoteItem.Status.AVAILABLE, Optional.of(second)),
                new MultiProductQuoteItem(2, 333, "Недоступный", MultiProductQuoteItem.Status.UNAVAILABLE, Optional.empty())
        ));
        when(context.sessions.findOwned(session.getId(), context.profile.getId())).thenReturn(Optional.of(session));
        Subscription existingTarget = mock(Subscription.class);
        UUID existingSubscriptionId = UUID.randomUUID();
        when(existingTarget.getId()).thenReturn(existingSubscriptionId);
        when(existingTarget.getNotificationMode())
                .thenReturn(com.priceradar.tracking.domain.NotificationMode.TARGET_PRICE);
        when(context.subscriptions.createFromQuote(
                context.profile.getId(), first.getQuoteSnapshotId(),
                com.priceradar.tracking.domain.NotificationMode.ANY_DECREASE, Optional.empty(), NOW
        )).thenReturn(SubscriptionCreationResult.created(mock(Subscription.class)));
        when(context.subscriptions.createFromQuote(
                context.profile.getId(), second.getQuoteSnapshotId(),
                com.priceradar.tracking.domain.NotificationMode.ANY_DECREASE, Optional.empty(), NOW
        )).thenReturn(SubscriptionCreationResult.alreadyActive(existingTarget));
        when(context.subscriptions.changeToAnyDecrease(
                context.profile.getId(), existingSubscriptionId, NOW
        )).thenReturn(SubscriptionConditionChangeResult.changed(mock(Subscription.class)));
        MultiProductQuoteCallbackCodec codec = new MultiProductQuoteCallbackCodec(SECRET);

        context.handler.handleCallback(new IncomingTelegramCallback(
                "callback", TELEGRAM_ID, TELEGRAM_ID, "private",
                codec.encode(MultiProductQuoteCallbackCodec.Action.ALL_MINIMUM, session.getId(), 0, TELEGRAM_ID)
        ));

        verify(context.subscriptions).createFromQuote(
                context.profile.getId(), first.getQuoteSnapshotId(),
                com.priceradar.tracking.domain.NotificationMode.ANY_DECREASE, Optional.empty(), NOW
        );
        verify(context.subscriptions).changeToAnyDecrease(
                context.profile.getId(), existingSubscriptionId, NOW
        );
        verify(context.subscriptions).createFromQuote(
                context.profile.getId(), second.getQuoteSnapshotId(),
                com.priceradar.tracking.domain.NotificationMode.ANY_DECREASE, Optional.empty(), NOW
        );
        var sent = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway).sendMessage(sent.capture());
        assertThat(sent.getValue().getText()).contains(
                "Добавлено: 1", "Режим изменён: 1", "Не удалось добавить: 1",
                "новой минимальной цене"
        );
    }

    private TestContext context() {
        UserProfileService users = mock(UserProfileService.class);
        ResolvedQuoteService single = mock(ResolvedQuoteService.class);
        ResolvedQuoteBatchService batch = mock(ResolvedQuoteBatchService.class);
        TelegramQuoteMessageFactory quoteMessages = mock(TelegramQuoteMessageFactory.class);
        MultiProductQuoteSessionStore sessions = mock(MultiProductQuoteSessionStore.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        UserProfile profile = new UserProfile(
                UUID.randomUUID(), TELEGRAM_ID, TELEGRAM_ID,
                moscow(), UserPricePreferences.defaults()
        );
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        MultiProductQuoteCallbackCodec codec = new MultiProductQuoteCallbackCodec(SECRET);
        TelegramCurrentQuoteHandler handler = new TelegramCurrentQuoteHandler(
                new WildberriesLinkExtractor(new ProductUrlParser(), new SharedBasketUrlParser(), 50),
                users, single, batch, quoteMessages,
                new TelegramMultiProductQuoteMessageFactory(codec, 50), codec, sessions, subscriptions, gateway,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15), 50
        );
        return new TestContext(handler, single, batch, quoteMessages, sessions, subscriptions, gateway, profile);
    }

    private ResolvedQuote quote(long nmId, String title, SnapshotStatus status) {
        InterpretedPrice price = status == SnapshotStatus.REGULAR_PRICE
                ? new InterpretedPrice(
                Optional.of(RubleAmount.ofMinorUnits(100_00)), Optional.empty(),
                Optional.of(PriceSource.PRODUCT), status
        ) : new InterpretedPrice(Optional.empty(), Optional.empty(), Optional.empty(), status);
        return new ResolvedQuote(
                UUID.randomUUID(), UUID.randomUUID(), Marketplace.WILDBERRIES, nmId,
                "https://www.wildberries.ru/catalog/" + nmId + "/detail.aspx",
                Optional.of(title), Optional.empty(), ResolvedVariant.noVariant(), price,
                moscow().toPriceContext(), NOW, NOW.plusSeconds(900)
        );
    }

    private BatchResolvedQuoteItem item(long nmId, ResolvedQuoteResult result, String title) {
        return new BatchResolvedQuoteItem(
                new ParsedProductUrl(nmId, OptionalLong.empty()), result, Optional.of(title)
        );
    }

    private void verifyNoBatch(TestContext context) {
        verify(context.batch, never()).resolve(any(), any());
        verify(context.sessions, never()).save(any());
    }

    private IncomingTelegramMessage message(String text) {
        return new IncomingTelegramMessage(TELEGRAM_ID, TELEGRAM_ID, "private", text);
    }

    private static final class TestContext {
        private final TelegramCurrentQuoteHandler handler;
        private final ResolvedQuoteService single;
        private final ResolvedQuoteBatchService batch;
        private final TelegramQuoteMessageFactory quoteMessages;
        private final MultiProductQuoteSessionStore sessions;
        private final SubscriptionService subscriptions;
        private final TelegramGateway gateway;
        private final UserProfile profile;

        private TestContext(
                TelegramCurrentQuoteHandler handler,
                ResolvedQuoteService single,
                ResolvedQuoteBatchService batch,
                TelegramQuoteMessageFactory quoteMessages,
                MultiProductQuoteSessionStore sessions,
                SubscriptionService subscriptions,
                TelegramGateway gateway,
                UserProfile profile
        ) {
            this.handler = handler;
            this.single = single;
            this.batch = batch;
            this.quoteMessages = quoteMessages;
            this.sessions = sessions;
            this.subscriptions = subscriptions;
            this.gateway = gateway;
            this.profile = profile;
        }
    }
}
