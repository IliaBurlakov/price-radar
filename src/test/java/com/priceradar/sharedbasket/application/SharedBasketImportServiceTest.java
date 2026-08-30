package com.priceradar.sharedbasket.application;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.product.application.PersistedResolvedQuote;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.product.application.ResolvedQuotePersistenceService;
import com.priceradar.tracking.application.SubscriptionQuoteObservation;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import com.priceradar.user.application.UserProfileStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static com.priceradar.testsupport.TestMarketplaceRegions.irkutsk;

class SharedBasketImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");
    private final PendingSharedBasketImportStore pendingStore = mock(PendingSharedBasketImportStore.class);
    private final UserProfileStore userStore = mock(UserProfileStore.class);
    private final SubscriptionStore subscriptionStore = mock(SubscriptionStore.class);
    private final SharedBasketImportService service = new SharedBasketImportService(
            new SharedBasketUrlParser(), mock(SharedBasketProvider.class),
            mock(SharedBasketProductResolver.class), new PriceSemanticsService(),
            mock(ResolvedQuotePersistenceService.class), pendingStore, userStore, subscriptionStore,
            50, Duration.ofMinutes(15)
    );

    @Test
    void addNewRevalidatesUnderUserLockAndNeverExceedsFifty() {
        UUID userId = UUID.randomUUID();
        List<Subscription> active = subscriptions(userId, 43);
        PendingSharedBasketImport pending = pending(userId, 10);
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        lockedUser(userId);
        when(subscriptionStore.findActiveSubscriptions(userId)).thenReturn(active);
        pending.getItems().forEach(item -> when(subscriptionStore.findQuoteObservation(item.getSnapshotId()))
                .thenReturn(Optional.of(observation(item))));
        when(subscriptionStore.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SharedBasketApplyResult result = service.apply(
                pending.getId(), userId, SharedBasketImportService.ApplyMode.ADD_NEW, NOW
        );

        assertThat(result.getAdded()).isEqualTo(7);
        assertThat(result.getSkippedByLimit()).isEqualTo(3);
        assertThat(result.getSkippedByLimitTitles())
                .containsExactly("Product 7", "Product 8", "Product 9");
        ArgumentCaptor<Subscription> created = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionStore, org.mockito.Mockito.times(7)).create(created.capture());
        assertThat(created.getAllValues()).allMatch(value ->
                value.getNotificationMode() == NotificationMode.ANY_DECREASE
                        && value.getNotificationReferencePrice()
                        .map(RubleAmount::getMinorUnits)
                        .filter(amount -> amount == 10000)
                        .isPresent()
        );
        verify(userStore).findByIdAndLock(userId);
    }

    @Test
    void synchronizeKeepsOverlapAndEndsOnlyAfterExplicitApply() {
        UUID userId = UUID.randomUUID();
        List<Subscription> active = subscriptions(userId, 23);
        List<PendingSharedBasketItem> items = new ArrayList<>();
        for (int index = 0; index < 15; index++) {
            items.add(item(index, active.get(index).getWatchTargetId()));
        }
        for (int index = 15; index < 30; index++) items.add(item(index, UUID.randomUUID()));
        PendingSharedBasketImport pending = new PendingSharedBasketImport(
                UUID.randomUUID(), userId, com.priceradar.testsupport.TestMarketplaceRegions.MOSCOW_ID, 30, 30, 0, List.of(), items,
                NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        lockedUser(userId);
        when(subscriptionStore.findActiveSubscriptions(userId)).thenReturn(active);
        items.forEach(item -> when(subscriptionStore.findQuoteObservation(item.getSnapshotId()))
                .thenReturn(Optional.of(observation(item))));
        when(subscriptionStore.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionStore.end(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SharedBasketApplyResult result = confirmedSynchronization(pending, userId);

        assertThat(result.getKept()).isEqualTo(15);
        assertThat(result.getAdded()).isEqualTo(15);
        assertThat(result.getEnded()).isEqualTo(8);
        verify(subscriptionStore, org.mockito.Mockito.times(15)).create(any());
        verify(subscriptionStore, org.mockito.Mockito.times(8)).end(any());
        for (int index = 0; index < 15; index++) {
            UUID overlapId = active.get(index).getId();
            verify(subscriptionStore, never()).end(org.mockito.ArgumentMatchers.argThat(
                    value -> value != null && value.getId().equals(overlapId)
            ));
        }
    }

    @Test
    void expiredImportCannotAcquireLockOrChangeSubscriptions() {
        UUID userId = UUID.randomUUID();
        PendingSharedBasketImport expired = new PendingSharedBasketImport(
                UUID.randomUUID(), userId, com.priceradar.testsupport.TestMarketplaceRegions.MOSCOW_ID, 0, 0, 0, List.of(), List.of(),
                NOW.minusSeconds(900), NOW
        );
        when(pendingStore.findOwned(expired.getId(), userId)).thenReturn(Optional.of(expired));

        SharedBasketApplyResult result = service.apply(
                expired.getId(), userId, SharedBasketImportService.ApplyMode.SYNCHRONIZE, NOW
        );

        assertThat(result.getStatus()).isEqualTo(SharedBasketApplyResult.Status.EXPIRED);
        verify(userStore, never()).findByIdAndLock(any());
        verify(subscriptionStore, never()).create(any());
        verify(subscriptionStore, never()).end(any());
    }

    @Test
    void unresolvedBasketItemMakesSynchronizationUnavailableAndCannotEndItsSubscription() {
        UUID userId = UUID.randomUUID();
        Subscription active = subscriptions(userId, 1).getFirst();
        PendingSharedBasketImport pending = new PendingSharedBasketImport(
                UUID.randomUUID(), userId, com.priceradar.testsupport.TestMarketplaceRegions.MOSCOW_ID, 1, 0, 1, List.of(), List.of(),
                NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));

        SharedBasketApplyResult result = service.apply(
                pending.getId(), userId, SharedBasketImportService.ApplyMode.SYNCHRONIZE,
                Optional.of("AbCdEf12345"), NOW
        );

        assertThat(result.getStatus())
                .isEqualTo(SharedBasketApplyResult.Status.SYNCHRONIZATION_UNAVAILABLE);
        verify(userStore, never()).findByIdAndLock(any());
        verify(subscriptionStore, never()).end(active);
        verify(subscriptionStore, never()).create(any());
    }

    @Test
    void unavailableBasketItemCannotBeAddedAndBlocksForgedSynchronization() {
        UUID userId = UUID.randomUUID();
        PendingSharedBasketImport pending = new PendingSharedBasketImport(
                UUID.randomUUID(), userId, com.priceradar.testsupport.TestMarketplaceRegions.MOSCOW_ID, 1, 0, 0,
                List.of(new PendingUnavailableSharedBasketItem(0, "Unavailable product")),
                List.of(), NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        lockedUser(userId);
        when(subscriptionStore.findActiveSubscriptions(userId)).thenReturn(List.of());

        SharedBasketApplyResult addResult = service.apply(
                pending.getId(), userId, SharedBasketImportService.ApplyMode.ADD_NEW, NOW
        );
        SharedBasketApplyResult syncResult = service.apply(
                pending.getId(), userId, SharedBasketImportService.ApplyMode.SYNCHRONIZE,
                Optional.of("AbCdEf12345"), NOW
        );

        assertThat(addResult.getStatus()).isEqualTo(SharedBasketApplyResult.Status.APPLIED);
        assertThat(addResult.getAdded()).isZero();
        assertThat(syncResult.getStatus())
                .isEqualTo(SharedBasketApplyResult.Status.SYNCHRONIZATION_UNAVAILABLE);
        verify(subscriptionStore, never()).create(any());
        verify(subscriptionStore, never()).end(any());
    }

    @Test
    void changedDestructivePlanIsRejectedBeforeAnySubscriptionChanges() {
        UUID userId = UUID.randomUUID();
        PendingSharedBasketImport pending = pending(userId, 1);
        Subscription originallyAbsent = subscriptions(userId, 1).getFirst();
        Subscription newlyAbsent = subscriptions(userId, 1).getFirst();
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        lockedUser(userId);
        when(subscriptionStore.findActiveSubscriptions(userId))
                .thenReturn(List.of(originallyAbsent))
                .thenReturn(List.of(originallyAbsent, newlyAbsent));

        SharedBasketPreview confirmedPreview = service.findPreview(pending.getId(), userId, NOW).orElseThrow();
        SharedBasketApplyResult result = service.apply(
                pending.getId(), userId, SharedBasketImportService.ApplyMode.SYNCHRONIZE,
                Optional.of(confirmedPreview.getDestructivePlanFingerprint()), NOW
        );

        assertThat(result.getStatus()).isEqualTo(SharedBasketApplyResult.Status.PLAN_CHANGED);
        verify(userStore).findByIdAndLock(userId);
        verify(subscriptionStore, never()).end(any());
        verify(subscriptionStore, never()).create(any());
    }

    @Test
    void prepareDeduplicatesExactItemsButKeepsDifferentVariantsInProviderOrder() {
        UUID userId = UUID.randomUUID();
        SharedBasketProvider provider = mock(SharedBasketProvider.class);
        SharedBasketProductResolver resolver = mock(SharedBasketProductResolver.class);
        PendingSharedBasketImportStore sessions = mock(PendingSharedBasketImportStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        SharedBasketImportService importService = new SharedBasketImportService(
                new SharedBasketUrlParser(), provider, resolver, new PriceSemanticsService(),
                mock(ResolvedQuotePersistenceService.class), sessions, mock(UserProfileStore.class), subscriptions,
                50, Duration.ofMinutes(15)
        );
        SharedBasketItem first = new SharedBasketItem(100, 1001, 1);
        SharedBasketItem duplicateWithQuantity = new SharedBasketItem(100, 1001, 3);
        SharedBasketItem anotherVariant = new SharedBasketItem(100, 1002, 1);
        when(provider.fetch("abc123def4")).thenReturn(SharedBasketProviderResult.success(
                new SharedBasket(List.of(first, duplicateWithQuantity, anotherVariant))
        ));
        when(resolver.resolveExact(any(), any())).thenReturn(SharedBasketProductResolution.success(
                List.of(), List.of(), List.of(
                        new UnresolvedSharedBasketItem(first, UnresolvedSharedBasketItem.Reason.PRODUCT_NOT_RETURNED),
                        new UnresolvedSharedBasketItem(anotherVariant, UnresolvedSharedBasketItem.Reason.VARIANT_NOT_FOUND)
                )
        ));
        when(subscriptions.findActiveSubscriptions(userId)).thenReturn(List.of());
        when(subscriptions.findActiveByUserId(userId)).thenReturn(List.of());
        UserProfile user = new UserProfile(
                userId, 7001L, 7001L, moscow(),
                UserPricePreferences.defaults()
        );

        importService.prepare(
                "https://www.wildberries.ru/basket?shareId=abc123def4", user, NOW
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SharedBasketItem>> items = ArgumentCaptor.forClass(List.class);
        verify(resolver).resolveExact(items.capture(), any());
        assertThat(items.getValue()).containsExactly(first, anotherVariant);
    }

    @Test
    void prepareKeepsAvailableCountIndependentFromDeduplicatedWatchTargets() {
        UUID userId = UUID.randomUUID();
        SharedBasketProvider provider = mock(SharedBasketProvider.class);
        SharedBasketProductResolver resolver = mock(SharedBasketProductResolver.class);
        ResolvedQuotePersistenceService quotes = mock(ResolvedQuotePersistenceService.class);
        PendingSharedBasketImportStore sessions = mock(PendingSharedBasketImportStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        SharedBasketImportService importService = new SharedBasketImportService(
                new SharedBasketUrlParser(), provider, resolver, new PriceSemanticsService(),
                quotes, sessions, mock(UserProfileStore.class), subscriptions,
                50, Duration.ofMinutes(15)
        );
        SharedBasketItem first = new SharedBasketItem(100, 1001, 1);
        SharedBasketItem second = new SharedBasketItem(100, 1002, 1);
        ResolvedSharedBasketItem firstResolved = resolved(first);
        ResolvedSharedBasketItem secondResolved = resolved(second);
        when(provider.fetch("abc123def4")).thenReturn(SharedBasketProviderResult.success(
                new SharedBasket(List.of(first, second))
        ));
        when(resolver.resolveExact(any(), any())).thenReturn(SharedBasketProductResolution.success(
                List.of(firstResolved, secondResolved), List.of(), List.of()
        ));
        UUID sharedTargetId = UUID.randomUUID();
        when(quotes.save(any())).thenReturn(
                new PersistedResolvedQuote(sharedTargetId, UUID.randomUUID()),
                new PersistedResolvedQuote(sharedTargetId, UUID.randomUUID())
        );
        when(subscriptions.findActiveSubscriptions(userId)).thenReturn(List.of());
        when(subscriptions.findActiveByUserId(userId)).thenReturn(List.of());
        UserProfile user = new UserProfile(
                userId, 7001L, 7001L, moscow(),
                UserPricePreferences.defaults()
        );

        importService.prepare("https://www.wildberries.ru/basket?shareId=abc123def4", user, NOW);

        ArgumentCaptor<PendingSharedBasketImport> pending = ArgumentCaptor.forClass(PendingSharedBasketImport.class);
        verify(sessions).save(pending.capture(), org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(pending.getValue().getFoundItems()).isEqualTo(2);
        assertThat(pending.getValue().getAvailableItems()).isEqualTo(2);
        assertThat(pending.getValue().getItems()).hasSize(1);
        assertThat(pending.getValue().getUnresolvedItems()).isZero();
        assertThat(pending.getValue().getUnavailableItems()).isEmpty();
    }

    @Test
    void sharedBasketUsesCurrentRegionAndOldPendingImportCannotBeApplied() {
        UUID userId = UUID.randomUUID();
        SharedBasketProvider provider = mock(SharedBasketProvider.class);
        SharedBasketProductResolver resolver = mock(SharedBasketProductResolver.class);
        PendingSharedBasketImportStore sessions = mock(PendingSharedBasketImportStore.class);
        UserProfileStore users = mock(UserProfileStore.class);
        SubscriptionStore subscriptions = mock(SubscriptionStore.class);
        SharedBasketImportService importService = new SharedBasketImportService(
                new SharedBasketUrlParser(), provider, resolver, new PriceSemanticsService(),
                mock(ResolvedQuotePersistenceService.class), sessions, users, subscriptions,
                50, Duration.ofMinutes(15)
        );
        UserProfile irkutskUser = new UserProfile(
                userId, 7001L, 7001L, irkutsk(), UserPricePreferences.defaults()
        );
        when(provider.fetch("abc123def4")).thenReturn(SharedBasketProviderResult.success(
                new SharedBasket(List.of())
        ));
        when(resolver.resolveExact(List.of(), irkutsk().toPriceContext()))
                .thenReturn(SharedBasketProductResolution.success(List.of(), List.of(), List.of()));
        when(subscriptions.findActiveSubscriptions(userId)).thenReturn(List.of());
        when(subscriptions.findActiveByUserId(userId)).thenReturn(List.of());

        importService.prepare(
                "https://www.wildberries.ru/basket?shareId=abc123def4", irkutskUser, NOW
        );

        verify(resolver).resolveExact(List.of(), irkutsk().toPriceContext());

        PendingSharedBasketImport oldMoscowImport = pending(userId, 1);
        when(sessions.findOwned(oldMoscowImport.getId(), userId))
                .thenReturn(Optional.of(oldMoscowImport));
        when(users.findByIdAndLock(userId)).thenReturn(Optional.of(irkutskUser));

        SharedBasketApplyResult result = importService.apply(
                oldMoscowImport.getId(), userId,
                SharedBasketImportService.ApplyMode.ADD_NEW, NOW
        );

        assertThat(result.getStatus()).isEqualTo(SharedBasketApplyResult.Status.REGION_MISMATCH);
        verify(subscriptions, never()).create(any());
        verify(subscriptions, never()).end(any());
    }

    @Test
    void previewDistinguishesTrackedItemsExcludedBySyncLimitFromAbsentItems() {
        UUID userId = UUID.randomUUID();
        List<PendingSharedBasketItem> items = new ArrayList<>();
        for (int index = 0; index < 73; index++) items.add(item(index, UUID.randomUUID()));
        Subscription trackedBeyondLimit = new Subscription(
                UUID.randomUUID(), userId, items.get(60).getWatchTargetId(), NotificationMode.ANY_DECREASE,
                Optional.empty(), Optional.empty(), Optional.empty(), ThresholdState.NOT_APPLICABLE,
                Optional.empty(), SubscriptionStatus.ACTIVE, NOW.minusSeconds(300), Optional.empty(), 0
        );
        PendingSharedBasketImport pending = new PendingSharedBasketImport(
                UUID.randomUUID(), userId, com.priceradar.testsupport.TestMarketplaceRegions.MOSCOW_ID, 73, 73, 0, List.of(), items,
                NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        when(subscriptionStore.findActiveSubscriptions(userId)).thenReturn(List.of(trackedBeyondLimit));
        when(subscriptionStore.findActiveByUserId(userId)).thenReturn(List.of());

        SharedBasketPreview preview = service.findPreview(pending.getId(), userId, NOW).orElseThrow();

        assertThat(preview.getSyncTargetItems()).isEqualTo(50);
        assertThat(preview.getSyncSkippedByLimit()).isEqualTo(23);
        assertThat(preview.getSyncSkippedTitles())
                .containsExactlyElementsOf(items.subList(50, 73).stream()
                        .map(item -> item.getTitle().orElseThrow())
                        .toList());
        assertThat(preview.getAddSkippedTitles())
                .allMatch(title -> title.startsWith("Product "))
                .hasSize(23);
        assertThat(preview.getAbsentTracked()).isZero();
        assertThat(preview.getExcludedByLimit()).isOne();
        assertThat(preview.getDestructiveRemovalCount()).isOne();
    }

    @Test
    void synchronizeSeventyThreeItemsAppliesExactlyTheFirstFifty() {
        UUID userId = UUID.randomUUID();
        List<PendingSharedBasketItem> items = new ArrayList<>();
        for (int index = 0; index < 73; index++) items.add(item(index, UUID.randomUUID()));
        Subscription trackedBeyondLimit = new Subscription(
                UUID.randomUUID(), userId, items.get(60).getWatchTargetId(), NotificationMode.ANY_DECREASE,
                Optional.empty(), Optional.empty(), Optional.empty(), ThresholdState.NOT_APPLICABLE,
                Optional.empty(), SubscriptionStatus.ACTIVE, NOW.minusSeconds(300), Optional.empty(), 0
        );
        PendingSharedBasketImport pending = new PendingSharedBasketImport(
                UUID.randomUUID(), userId, com.priceradar.testsupport.TestMarketplaceRegions.MOSCOW_ID, 73, 73, 0, List.of(), items,
                NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        lockedUser(userId);
        when(subscriptionStore.findActiveSubscriptions(userId)).thenReturn(List.of(trackedBeyondLimit));
        items.forEach(item -> when(subscriptionStore.findQuoteObservation(item.getSnapshotId()))
                .thenReturn(Optional.of(observation(item))));
        when(subscriptionStore.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionStore.end(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SharedBasketApplyResult result = confirmedSynchronization(pending, userId);

        assertThat(result.getAdded()).isEqualTo(50);
        assertThat(result.getEnded()).isOne();
        assertThat(result.getSkippedByLimit()).isEqualTo(23);
        assertThat(result.getSkippedByLimitTitles())
                .containsExactlyElementsOf(items.subList(50, 73).stream()
                        .map(item -> item.getTitle().orElseThrow())
                        .toList());
        ArgumentCaptor<Subscription> created = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionStore, org.mockito.Mockito.times(50)).create(created.capture());
        assertThat(created.getAllValues())
                .extracting(Subscription::getWatchTargetId)
                .containsExactlyElementsOf(items.stream()
                        .limit(50)
                        .map(PendingSharedBasketItem::getWatchTargetId)
                        .toList());
    }

    private PendingSharedBasketImport pending(UUID userId, int count) {
        List<PendingSharedBasketItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) items.add(item(index, UUID.randomUUID()));
        return new PendingSharedBasketImport(
                UUID.randomUUID(), userId, com.priceradar.testsupport.TestMarketplaceRegions.MOSCOW_ID,
                count, count, 0, List.of(), items,
                NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
    }

    private SharedBasketApplyResult confirmedSynchronization(PendingSharedBasketImport pending, UUID userId) {
        SharedBasketPreview preview = service.findPreview(pending.getId(), userId, NOW).orElseThrow();
        return service.apply(
                pending.getId(), userId, SharedBasketImportService.ApplyMode.SYNCHRONIZE,
                Optional.of(preview.getDestructivePlanFingerprint()), NOW
        );
    }

    private PendingSharedBasketItem item(int position, UUID targetId) {
        return new PendingSharedBasketItem(position, targetId, UUID.randomUUID(), Optional.of("Product " + position));
    }

    private ResolvedSharedBasketItem resolved(SharedBasketItem basketItem) {
        MarketplaceProductDetails product = new MarketplaceProductDetails(
                Marketplace.WILDBERRIES,
                String.valueOf(basketItem.getNmId()),
                Optional.of("Product " + basketItem.getChrtId()),
                Optional.empty(),
                List.of(),
                java.util.Map.of()
        );
        return new ResolvedSharedBasketItem(
                basketItem,
                product,
                ResolvedVariant.wildberriesSize(basketItem.getChrtId(), List.of(), false),
                new ProviderPriceFields(
                        true, Optional.of(RubleAmount.ofMinorUnits(10000)), Optional.empty()
                ),
                NOW.minusSeconds(30)
        );
    }

    private SubscriptionQuoteObservation observation(PendingSharedBasketItem item) {
        return new SubscriptionQuoteObservation(
                item.getSnapshotId(), item.getWatchTargetId(), NOW.minusSeconds(30),
                Optional.of(RubleAmount.ofMinorUnits(10000)), moscow().toPriceContext()
        );
    }

    private void lockedUser(UUID userId) {
        when(userStore.findByIdAndLock(userId)).thenReturn(Optional.of(new UserProfile(
                userId, 7001L, 7001L, moscow(), UserPricePreferences.defaults()
        )));
    }

    private List<Subscription> subscriptions(UUID userId, int count) {
        List<Subscription> subscriptions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            subscriptions.add(new Subscription(
                    UUID.randomUUID(), userId, UUID.randomUUID(), NotificationMode.ANY_DECREASE,
                    Optional.empty(), Optional.of(RubleAmount.ofMinorUnits(20000)),
                    Optional.of(NOW.minusSeconds(120)), ThresholdState.NOT_APPLICABLE,
                    Optional.empty(), SubscriptionStatus.ACTIVE, NOW.minusSeconds(300), Optional.empty(), 0
            ));
        }
        return subscriptions;
    }
}
