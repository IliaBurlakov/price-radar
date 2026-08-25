package com.priceradar.sharedbasket.application;

import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.PriceContext;
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

class SharedBasketImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");
    private final PendingSharedBasketImportStore pendingStore = mock(PendingSharedBasketImportStore.class);
    private final UserProfileStore userStore = mock(UserProfileStore.class);
    private final SubscriptionStore subscriptionStore = mock(SubscriptionStore.class);
    private final SharedBasketImportService service = new SharedBasketImportService(
            new SharedBasketUrlParser(), mock(SharedBasketProvider.class),
            mock(SharedBasketProductResolver.class), new PriceSemanticsService(),
            mock(ResolvedQuotePersistenceService.class), pendingStore, userStore, subscriptionStore
    );

    @Test
    void addNewRevalidatesUnderUserLockAndNeverExceedsFifty() {
        UUID userId = UUID.randomUUID();
        List<Subscription> active = subscriptions(userId, 43);
        PendingSharedBasketImport pending = pending(userId, 10);
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        when(userStore.existsAndLockById(userId)).thenReturn(true);
        when(subscriptionStore.findActiveSubscriptions(userId)).thenReturn(active);
        pending.getItems().forEach(item -> when(subscriptionStore.findQuoteObservation(item.getSnapshotId()))
                .thenReturn(Optional.of(observation(item))));
        when(subscriptionStore.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SharedBasketApplyResult result = service.apply(
                pending.getId(), userId, SharedBasketImportService.ApplyMode.ADD_NEW, NOW
        );

        assertThat(result.getAdded()).isEqualTo(7);
        assertThat(result.getSkippedByLimit()).isEqualTo(3);
        ArgumentCaptor<Subscription> created = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionStore, org.mockito.Mockito.times(7)).create(created.capture());
        assertThat(created.getAllValues()).allMatch(value ->
                value.getNotificationMode() == NotificationMode.ANY_DECREASE
                        && value.getNotificationReferencePrice()
                        .map(RubleAmount::getMinorUnits)
                        .filter(amount -> amount == 10000)
                        .isPresent()
        );
        verify(userStore).existsAndLockById(userId);
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
                UUID.randomUUID(), userId, 30, 0, items, NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        when(userStore.existsAndLockById(userId)).thenReturn(true);
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
                UUID.randomUUID(), userId, 0, 0, List.of(), NOW.minusSeconds(900), NOW
        );
        when(pendingStore.findOwned(expired.getId(), userId)).thenReturn(Optional.of(expired));

        SharedBasketApplyResult result = service.apply(
                expired.getId(), userId, SharedBasketImportService.ApplyMode.SYNCHRONIZE, NOW
        );

        assertThat(result.getStatus()).isEqualTo(SharedBasketApplyResult.Status.EXPIRED);
        verify(userStore, never()).existsAndLockById(any());
        verify(subscriptionStore, never()).create(any());
        verify(subscriptionStore, never()).end(any());
    }

    @Test
    void skippedBasketItemMakesSynchronizationUnavailableAndCannotEndItsSubscription() {
        UUID userId = UUID.randomUUID();
        Subscription active = subscriptions(userId, 1).getFirst();
        PendingSharedBasketImport pending = new PendingSharedBasketImport(
                UUID.randomUUID(), userId, 1, 1, List.of(),
                NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));

        SharedBasketApplyResult result = service.apply(
                pending.getId(), userId, SharedBasketImportService.ApplyMode.SYNCHRONIZE,
                Optional.of("AbCdEf12345"), NOW
        );

        assertThat(result.getStatus())
                .isEqualTo(SharedBasketApplyResult.Status.SYNCHRONIZATION_UNAVAILABLE);
        verify(userStore, never()).existsAndLockById(any());
        verify(subscriptionStore, never()).end(active);
        verify(subscriptionStore, never()).create(any());
    }

    @Test
    void changedDestructivePlanIsRejectedBeforeAnySubscriptionChanges() {
        UUID userId = UUID.randomUUID();
        PendingSharedBasketImport pending = pending(userId, 1);
        Subscription originallyAbsent = subscriptions(userId, 1).getFirst();
        Subscription newlyAbsent = subscriptions(userId, 1).getFirst();
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        when(userStore.existsAndLockById(userId)).thenReturn(true);
        when(subscriptionStore.findActiveSubscriptions(userId))
                .thenReturn(List.of(originallyAbsent))
                .thenReturn(List.of(originallyAbsent, newlyAbsent));

        SharedBasketPreview confirmedPreview = service.findPreview(pending.getId(), userId, NOW).orElseThrow();
        SharedBasketApplyResult result = service.apply(
                pending.getId(), userId, SharedBasketImportService.ApplyMode.SYNCHRONIZE,
                Optional.of(confirmedPreview.getDestructivePlanFingerprint()), NOW
        );

        assertThat(result.getStatus()).isEqualTo(SharedBasketApplyResult.Status.PLAN_CHANGED);
        verify(userStore).existsAndLockById(userId);
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
                mock(ResolvedQuotePersistenceService.class), sessions, mock(UserProfileStore.class), subscriptions
        );
        SharedBasketItem first = new SharedBasketItem(100, 1001, 1);
        SharedBasketItem duplicateWithQuantity = new SharedBasketItem(100, 1001, 3);
        SharedBasketItem anotherVariant = new SharedBasketItem(100, 1002, 1);
        when(provider.fetch("abc123def4")).thenReturn(SharedBasketProviderResult.success(
                new SharedBasket(List.of(first, duplicateWithQuantity, anotherVariant))
        ));
        when(resolver.resolveExact(any(), any())).thenReturn(SharedBasketProductResolution.success(List.of(), 2));
        when(subscriptions.findActiveSubscriptions(userId)).thenReturn(List.of());
        when(subscriptions.findActiveByUserId(userId)).thenReturn(List.of());
        UserProfile user = new UserProfile(
                userId, 7001L, 7001L, new PriceContext("Moscow", 1259570991L, 30),
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
                UUID.randomUUID(), userId, 73, 0, items, NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        when(subscriptionStore.findActiveSubscriptions(userId)).thenReturn(List.of(trackedBeyondLimit));
        when(subscriptionStore.findActiveByUserId(userId)).thenReturn(List.of());

        SharedBasketPreview preview = service.findPreview(pending.getId(), userId, NOW).orElseThrow();

        assertThat(preview.getSyncTargetItems()).isEqualTo(50);
        assertThat(preview.getSyncSkippedByLimit()).isEqualTo(23);
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
                UUID.randomUUID(), userId, 73, 0, items, NOW.minusSeconds(30), NOW.plusSeconds(600)
        );
        when(pendingStore.findOwned(pending.getId(), userId)).thenReturn(Optional.of(pending));
        when(userStore.existsAndLockById(userId)).thenReturn(true);
        when(subscriptionStore.findActiveSubscriptions(userId)).thenReturn(List.of(trackedBeyondLimit));
        items.forEach(item -> when(subscriptionStore.findQuoteObservation(item.getSnapshotId()))
                .thenReturn(Optional.of(observation(item))));
        when(subscriptionStore.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionStore.end(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SharedBasketApplyResult result = confirmedSynchronization(pending, userId);

        assertThat(result.getAdded()).isEqualTo(50);
        assertThat(result.getEnded()).isOne();
        assertThat(result.getSkippedByLimit()).isEqualTo(23);
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
                UUID.randomUUID(), userId, count, 0, items, NOW.minusSeconds(30), NOW.plusSeconds(600)
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

    private SubscriptionQuoteObservation observation(PendingSharedBasketItem item) {
        return new SubscriptionQuoteObservation(
                item.getSnapshotId(), item.getWatchTargetId(), NOW.minusSeconds(30),
                Optional.of(RubleAmount.ofMinorUnits(10000))
        );
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
