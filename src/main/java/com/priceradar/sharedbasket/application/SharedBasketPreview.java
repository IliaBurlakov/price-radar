package com.priceradar.sharedbasket.application;

import java.util.List;
import java.util.UUID;

public final class SharedBasketPreview {

    private final UUID importId;
    private final UUID locationId;
    private final int foundItems;
    private final int availableItems;
    private final int readyItems;
    private final int unresolvedItems;
    private final List<String> unavailableTitles;
    private final int alreadyTracked;
    private final int newItems;
    private final int absentTracked;
    private final int excludedByLimit;
    private final int activeSubscriptionLimit;
    private final int freeSlots;
    private final int addableItems;
    private final int syncTargetItems;
    private final List<String> addSkippedTitles;
    private final List<String> syncSkippedTitles;
    private final List<String> absentTitles;
    private final List<String> excludedByLimitTitles;
    private final String destructivePlanFingerprint;

    public SharedBasketPreview(
            UUID importId, UUID locationId,
            int foundItems, int availableItems, int readyItems,
            int unresolvedItems, List<String> unavailableTitles,
            int alreadyTracked, int newItems, int absentTracked, int excludedByLimit,
            int activeSubscriptionLimit, int freeSlots,
            int addableItems, int syncTargetItems,
            List<String> addSkippedTitles, List<String> syncSkippedTitles, List<String> absentTitles,
            List<String> excludedByLimitTitles, String destructivePlanFingerprint
    ) {
        this.importId = importId;
        this.locationId = java.util.Objects.requireNonNull(locationId);
        this.foundItems = foundItems;
        this.availableItems = availableItems;
        this.readyItems = readyItems;
        this.unresolvedItems = unresolvedItems;
        this.unavailableTitles = List.copyOf(unavailableTitles);
        this.alreadyTracked = alreadyTracked;
        this.newItems = newItems;
        this.absentTracked = absentTracked;
        this.excludedByLimit = excludedByLimit;
        if (activeSubscriptionLimit <= 0 || freeSlots < 0 || freeSlots > activeSubscriptionLimit) {
            throw new IllegalArgumentException("subscription capacity is invalid");
        }
        this.activeSubscriptionLimit = activeSubscriptionLimit;
        this.freeSlots = freeSlots;
        this.addableItems = addableItems;
        this.syncTargetItems = syncTargetItems;
        this.addSkippedTitles = List.copyOf(addSkippedTitles);
        this.syncSkippedTitles = List.copyOf(syncSkippedTitles);
        this.absentTitles = List.copyOf(absentTitles);
        this.excludedByLimitTitles = List.copyOf(excludedByLimitTitles);
        this.destructivePlanFingerprint = java.util.Objects.requireNonNull(destructivePlanFingerprint);
    }

    public UUID getImportId() { return importId; }
    public UUID getLocationId() { return locationId; }
    public int getFoundItems() { return foundItems; }
    public int getAvailableItems() { return availableItems; }
    public int getReadyItems() { return readyItems; }
    public int getUnavailableItems() { return unavailableTitles.size(); }
    public int getUnresolvedItems() { return unresolvedItems; }
    public List<String> getUnavailableTitles() { return unavailableTitles; }
    public int getAlreadyTracked() { return alreadyTracked; }
    public int getNewItems() { return newItems; }
    public int getAbsentTracked() { return absentTracked; }
    public int getExcludedByLimit() { return excludedByLimit; }
    public int getDestructiveRemovalCount() { return absentTracked + excludedByLimit; }
    public int getActiveSubscriptionLimit() { return activeSubscriptionLimit; }
    public int getFreeSlots() { return freeSlots; }
    public int getAddableItems() { return addableItems; }
    public int getAddSkippedByLimit() { return addSkippedTitles.size(); }
    public List<String> getAddSkippedTitles() { return addSkippedTitles; }
    public int getSyncTargetItems() { return syncTargetItems; }
    public int getSyncSkippedByLimit() { return syncSkippedTitles.size(); }
    public List<String> getSyncSkippedTitles() { return syncSkippedTitles; }
    public boolean isSynchronizationAvailable() {
        return unavailableTitles.isEmpty() && unresolvedItems == 0;
    }
    public List<String> getAbsentTitles() { return absentTitles; }
    public List<String> getExcludedByLimitTitles() { return excludedByLimitTitles; }
    public String getDestructivePlanFingerprint() { return destructivePlanFingerprint; }
}
