package com.priceradar.sharedbasket.application;

import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.product.application.PersistedResolvedQuote;
import com.priceradar.product.application.ResolvedQuotePersistenceCommand;
import com.priceradar.product.application.ResolvedQuotePersistenceService;
import com.priceradar.tracking.application.SubscriptionQuoteObservation;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileStore;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SharedBasketImportService {

    public enum ApplyMode { ADD_NEW, SYNCHRONIZE }

    private static final String CANONICAL_URL = "https://www.wildberries.ru/catalog/%d/detail.aspx";

    private final SharedBasketUrlParser urlParser;
    private final SharedBasketProvider basketProvider;
    private final SharedBasketProductResolver productResolver;
    private final PriceSemanticsService priceSemanticsService;
    private final ResolvedQuotePersistenceService quotePersistenceService;
    private final PendingSharedBasketImportStore pendingStore;
    private final UserProfileStore userProfileStore;
    private final SubscriptionStore subscriptionStore;
    private final int activeSubscriptionLimit;
    private final Duration importTtl;

    public SharedBasketImportService(
            SharedBasketUrlParser urlParser,
            SharedBasketProvider basketProvider,
            SharedBasketProductResolver productResolver,
            PriceSemanticsService priceSemanticsService,
            ResolvedQuotePersistenceService quotePersistenceService,
            PendingSharedBasketImportStore pendingStore,
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore,
            int activeSubscriptionLimit,
            Duration importTtl
    ) {
        this.urlParser = java.util.Objects.requireNonNull(urlParser);
        this.basketProvider = java.util.Objects.requireNonNull(basketProvider);
        this.productResolver = java.util.Objects.requireNonNull(productResolver);
        this.priceSemanticsService = java.util.Objects.requireNonNull(priceSemanticsService);
        this.quotePersistenceService = java.util.Objects.requireNonNull(quotePersistenceService);
        this.pendingStore = java.util.Objects.requireNonNull(pendingStore);
        this.userProfileStore = java.util.Objects.requireNonNull(userProfileStore);
        this.subscriptionStore = java.util.Objects.requireNonNull(subscriptionStore);
        if (activeSubscriptionLimit <= 0 || importTtl == null || importTtl.isZero() || importTtl.isNegative()) {
            throw new IllegalArgumentException("shared basket policy values must be positive");
        }
        this.activeSubscriptionLimit = activeSubscriptionLimit;
        this.importTtl = importTtl;
    }

    public SharedBasketPreviewResult prepare(String rawUrl, UserProfile user, Instant now) {
        if (user == null || now == null) throw new IllegalArgumentException("user and now must not be null");
        final String shareId;
        try {
            shareId = urlParser.parse(rawUrl);
        } catch (InvalidSharedBasketUrlException exception) {
            return SharedBasketPreviewResult.failed(SharedBasketPreviewResult.Status.INVALID_URL);
        }

        SharedBasketProviderResult basketResult = basketProvider.fetch(shareId);
        if (!basketResult.isSuccess()) return providerFailure(basketResult.getFailure().orElseThrow());

        List<SharedBasketItem> uniqueItems = new ArrayList<>(new LinkedHashSet<>(
                basketResult.getBasket().orElseThrow().getItems()
        ));
        SharedBasketProductResolution resolution = productResolver.resolveExact(
                uniqueItems,
                user.getPriceContext()
        );
        if (!resolution.isSuccess()) return providerFailure(resolution.getFailure().orElseThrow());
        int classifiedItems = resolution.getResolvedItems().size()
                + resolution.getUnavailableItems().size()
                + resolution.getUnresolvedItems().size();
        if (classifiedItems != uniqueItems.size()) {
            return SharedBasketPreviewResult.failed(
                    SharedBasketPreviewResult.Status.TEMPORARILY_UNAVAILABLE
            );
        }

        List<PendingSharedBasketItem> pendingItems = persistResolvedItems(
                resolution.getResolvedItems(),
                user
        );
        List<PendingUnavailableSharedBasketItem> unavailableItems = new ArrayList<>();
        for (int index = 0; index < resolution.getUnavailableItems().size(); index++) {
            unavailableItems.add(new PendingUnavailableSharedBasketItem(
                    index, resolution.getUnavailableItems().get(index).getDisplayName()
            ));
        }
        PendingSharedBasketImport pendingImport = new PendingSharedBasketImport(
                UUID.randomUUID(),
                user.getId(),
                user.getLocation().orElseThrow().getId(),
                uniqueItems.size(),
                resolution.getResolvedItems().size(),
                resolution.getUnresolvedItems().size(),
                unavailableItems,
                pendingItems,
                now,
                now.plus(importTtl)
        );
        pendingStore.save(pendingImport, now);
        return SharedBasketPreviewResult.ready(buildPreview(pendingImport, user.getId()));
    }

    public Optional<SharedBasketPreview> findPreview(UUID importId, UUID userId, Instant now) {
        if (importId == null || userId == null || now == null) throw new IllegalArgumentException("preview identity must not be null");
        return pendingStore.findOwned(importId, userId)
                .filter(value -> !value.isExpired(now))
                .map(value -> buildPreview(value, userId));
    }

    @Transactional
    public SharedBasketApplyResult apply(UUID importId, UUID userId, ApplyMode mode, Instant now) {
        return apply(importId, userId, mode, Optional.empty(), now);
    }

    @Transactional
    public SharedBasketApplyResult apply(
            UUID importId,
            UUID userId,
            ApplyMode mode,
            Optional<String> confirmedPlanFingerprint,
            Instant now
    ) {
        if (importId == null || userId == null || mode == null || now == null) {
            throw new IllegalArgumentException("shared basket apply fields must not be null");
        }
        if (confirmedPlanFingerprint == null) {
            throw new IllegalArgumentException("confirmedPlanFingerprint must not be null");
        }
        Optional<PendingSharedBasketImport> pending = pendingStore.findOwned(importId, userId);
        if (pending.isEmpty() || pending.orElseThrow().isExpired(now)) {
            pendingStore.remove(importId, userId);
            return SharedBasketApplyResult.failed(SharedBasketApplyResult.Status.EXPIRED);
        }
        if (mode == ApplyMode.SYNCHRONIZE
                && (!pending.orElseThrow().getUnavailableItems().isEmpty()
                || pending.orElseThrow().getUnresolvedItems() > 0)) {
            return SharedBasketApplyResult.failed(SharedBasketApplyResult.Status.SYNCHRONIZATION_UNAVAILABLE);
        }
        UserProfile lockedUser = userProfileStore.findByIdAndLock(userId).orElse(null);
        if (lockedUser == null) {
            return SharedBasketApplyResult.failed(SharedBasketApplyResult.Status.USER_NOT_FOUND);
        }
        if (!lockedUser.getLocation().orElseThrow().getId().equals(pending.orElseThrow().getLocationId())) {
            return SharedBasketApplyResult.failed(SharedBasketApplyResult.Status.REGION_MISMATCH);
        }

        List<Subscription> active = subscriptionStore.findActiveSubscriptions(userId);
        Map<UUID, Subscription> activeByTarget = new LinkedHashMap<>();
        active.forEach(subscription -> activeByTarget.put(subscription.getWatchTargetId(), subscription));

        List<PendingSharedBasketItem> targetItems = mode == ApplyMode.SYNCHRONIZE
                ? pending.orElseThrow().getItems().stream().limit(activeSubscriptionLimit).toList()
                : pending.orElseThrow().getItems();
        Set<UUID> targetIds = targetItems.stream()
                .map(PendingSharedBasketItem::getWatchTargetId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<Subscription> destructivePlan = active.stream()
                .filter(subscription -> !targetIds.contains(subscription.getWatchTargetId()))
                .toList();
        if (mode == ApplyMode.SYNCHRONIZE && !destructivePlan.isEmpty()
                && !confirmedPlanFingerprint.filter(destructivePlanFingerprint(destructivePlan)::equals).isPresent()) {
            return SharedBasketApplyResult.failed(SharedBasketApplyResult.Status.PLAN_CHANGED);
        }

        int ended = 0;
        if (mode == ApplyMode.SYNCHRONIZE) {
            for (Subscription subscription : destructivePlan) {
                subscriptionStore.end(subscription.end(now));
                activeByTarget.remove(subscription.getWatchTargetId());
                ended++;
            }
        }

        int kept = (int) targetIds.stream().filter(activeByTarget::containsKey).count();
        int freeSlots = activeSubscriptionLimit - activeByTarget.size();
        int added = 0;
        List<String> skippedByLimitTitles = new ArrayList<>();
        for (PendingSharedBasketItem item : targetItems) {
            if (activeByTarget.containsKey(item.getWatchTargetId())) continue;
            if (added >= freeSlots) {
                skippedByLimitTitles.add(displayName(item));
                continue;
            }
            SubscriptionQuoteObservation observation = subscriptionStore
                    .findQuoteObservation(item.getSnapshotId())
                    .orElseThrow(() -> new IllegalStateException("Pending basket snapshot disappeared"));
            if (!observation.getWatchTargetId().equals(item.getWatchTargetId())) {
                throw new IllegalStateException("Pending basket snapshot does not belong to its watch target");
            }
            Subscription created = createAnyDecrease(userId, observation, now);
            subscriptionStore.create(created);
            activeByTarget.put(item.getWatchTargetId(), created);
            added++;
        }

        if (mode == ApplyMode.SYNCHRONIZE) {
            skippedByLimitTitles = pending.orElseThrow().getItems().stream()
                    .skip(activeSubscriptionLimit)
                    .map(this::displayName)
                    .toList();
        }
        pendingStore.remove(importId, userId);
        return SharedBasketApplyResult.applied(added, kept, ended, skippedByLimitTitles);
    }

    @Transactional
    public void cancel(UUID importId, UUID userId) {
        pendingStore.remove(importId, userId);
    }

    private List<PendingSharedBasketItem> persistResolvedItems(
            List<ResolvedSharedBasketItem> resolvedItems,
            UserProfile user
    ) {
        Map<UUID, PendingSharedBasketItem> byTarget = new LinkedHashMap<>();
        for (ResolvedSharedBasketItem item : resolvedItems) {
            InterpretedPrice price = priceSemanticsService.interpret(item.getPriceFields());
            ResolvedQuotePersistenceCommand command = new ResolvedQuotePersistenceCommand(
                    item.getProduct(),
                    item.getBasketItem().getNmId(),
                    CANONICAL_URL.formatted(item.getBasketItem().getNmId()),
                    item.getVariant(),
                    user.getPriceContext(),
                    price,
                    item.getObservedAt()
            );
            PersistedResolvedQuote quote = quotePersistenceService.save(command);
            byTarget.putIfAbsent(quote.getWatchTargetId(), new PendingSharedBasketItem(
                    byTarget.size(), quote.getWatchTargetId(), quote.getSnapshotId(), item.getProduct().getTitle()
            ));
        }
        return new ArrayList<>(byTarget.values());
    }

    private SharedBasketPreview buildPreview(PendingSharedBasketImport pending, UUID userId) {
        List<Subscription> active = subscriptionStore.findActiveSubscriptions(userId);
        Set<UUID> activeTargets = active.stream()
                .map(Subscription::getWatchTargetId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> basketTargets = pending.getItems().stream()
                .map(PendingSharedBasketItem::getWatchTargetId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> syncTargets = pending.getItems().stream()
                .limit(activeSubscriptionLimit)
                .map(PendingSharedBasketItem::getWatchTargetId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int overlap = (int) basketTargets.stream().filter(activeTargets::contains).count();
        List<PendingSharedBasketItem> newPendingItems = pending.getItems().stream()
                .filter(item -> !activeTargets.contains(item.getWatchTargetId()))
                .toList();
        int newItems = newPendingItems.size();
        int freeSlots = Math.max(0, activeSubscriptionLimit - activeTargets.size());
        List<String> addSkippedTitles = newPendingItems.stream()
                .skip(freeSlots)
                .map(this::displayName)
                .toList();
        List<String> syncSkippedTitles = pending.getItems().stream()
                .skip(activeSubscriptionLimit)
                .map(this::displayName)
                .toList();
        List<TrackedSubscriptionItem> trackedItems = subscriptionStore.findActiveByUserId(userId);
        Map<UUID, String> titleBySubscription = new LinkedHashMap<>();
        trackedItems.forEach(item -> titleBySubscription.put(
                item.getSubscriptionId(),
                item.getTitle().orElse("Товар Wildberries")
        ));
        List<String> absentTitles = active.stream()
                .filter(subscription -> !basketTargets.contains(subscription.getWatchTargetId()))
                .map(subscription -> titleBySubscription.getOrDefault(subscription.getId(), "Товар Wildberries"))
                .toList();
        List<String> excludedByLimitTitles = active.stream()
                .filter(subscription -> basketTargets.contains(subscription.getWatchTargetId()))
                .filter(subscription -> !syncTargets.contains(subscription.getWatchTargetId()))
                .map(subscription -> titleBySubscription.getOrDefault(subscription.getId(), "Товар Wildberries"))
                .toList();
        List<Subscription> destructivePlan = active.stream()
                .filter(subscription -> !syncTargets.contains(subscription.getWatchTargetId()))
                .toList();
        return new SharedBasketPreview(
                pending.getId(), pending.getLocationId(), pending.getFoundItems(),
                pending.getAvailableItems(), pending.getItems().size(),
                pending.getUnresolvedItems(), pending.getUnavailableItems().stream()
                        .map(PendingUnavailableSharedBasketItem::getDisplayName)
                        .toList(),
                overlap, newItems, absentTitles.size(), excludedByLimitTitles.size(), freeSlots,
                Math.min(newItems, freeSlots), Math.min(pending.getItems().size(), activeSubscriptionLimit),
                addSkippedTitles, syncSkippedTitles, absentTitles, excludedByLimitTitles,
                destructivePlanFingerprint(destructivePlan)
        );
    }

    private String displayName(PendingSharedBasketItem item) {
        return item.getTitle().orElse("Товар Wildberries #" + (item.getPosition() + 1));
    }

    private String destructivePlanFingerprint(List<Subscription> subscriptions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            subscriptions.stream()
                    .map(Subscription::getId)
                    .sorted()
                    .forEach(id -> digest.update(id.toString().getBytes(StandardCharsets.US_ASCII)));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    java.util.Arrays.copyOf(digest.digest(), 8)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Subscription createAnyDecrease(
            UUID userId,
            SubscriptionQuoteObservation observation,
            Instant now
    ) {
        return new Subscription(
                UUID.randomUUID(), userId, observation.getWatchTargetId(), NotificationMode.ANY_DECREASE,
                Optional.empty(), observation.getRegularPrice(),
                observation.getRegularPrice().map(ignored -> observation.getObservedAt()),
                ThresholdState.NOT_APPLICABLE, Optional.empty(), SubscriptionStatus.ACTIVE,
                now, Optional.empty(), 0
        );
    }

    private SharedBasketPreviewResult providerFailure(SharedBasketFailure failure) {
        return SharedBasketPreviewResult.failed(failure.getCode() == SharedBasketFailureCode.NOT_FOUND
                ? SharedBasketPreviewResult.Status.NOT_FOUND
                : SharedBasketPreviewResult.Status.TEMPORARILY_UNAVAILABLE);
    }
}
