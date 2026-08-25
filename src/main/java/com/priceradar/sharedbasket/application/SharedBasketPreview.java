package com.priceradar.sharedbasket.application;

import java.util.List;
import java.util.UUID;

public final class SharedBasketPreview {

    private final UUID importId;
    private final int foundItems;
    private final int readyItems;
    private final int skippedItems;
    private final int alreadyTracked;
    private final int newItems;
    private final int absentTracked;
    private final int excludedByLimit;
    private final int freeSlots;
    private final int addableItems;
    private final int syncTargetItems;
    private final List<String> absentTitles;
    private final List<String> excludedByLimitTitles;
    private final String destructivePlanFingerprint;

    public SharedBasketPreview(
            UUID importId, int foundItems, int readyItems, int skippedItems,
            int alreadyTracked, int newItems, int absentTracked, int excludedByLimit, int freeSlots,
            int addableItems, int syncTargetItems, List<String> absentTitles,
            List<String> excludedByLimitTitles, String destructivePlanFingerprint
    ) {
        this.importId = importId;
        this.foundItems = foundItems;
        this.readyItems = readyItems;
        this.skippedItems = skippedItems;
        this.alreadyTracked = alreadyTracked;
        this.newItems = newItems;
        this.absentTracked = absentTracked;
        this.excludedByLimit = excludedByLimit;
        this.freeSlots = freeSlots;
        this.addableItems = addableItems;
        this.syncTargetItems = syncTargetItems;
        this.absentTitles = List.copyOf(absentTitles);
        this.excludedByLimitTitles = List.copyOf(excludedByLimitTitles);
        this.destructivePlanFingerprint = java.util.Objects.requireNonNull(destructivePlanFingerprint);
    }

    public UUID getImportId() { return importId; }
    public int getFoundItems() { return foundItems; }
    public int getReadyItems() { return readyItems; }
    public int getSkippedItems() { return skippedItems; }
    public int getAlreadyTracked() { return alreadyTracked; }
    public int getNewItems() { return newItems; }
    public int getAbsentTracked() { return absentTracked; }
    public int getExcludedByLimit() { return excludedByLimit; }
    public int getDestructiveRemovalCount() { return absentTracked + excludedByLimit; }
    public int getFreeSlots() { return freeSlots; }
    public int getAddableItems() { return addableItems; }
    public int getAddSkippedByLimit() { return Math.max(0, newItems - addableItems); }
    public int getSyncTargetItems() { return syncTargetItems; }
    public int getSyncSkippedByLimit() { return Math.max(0, readyItems - syncTargetItems); }
    public boolean isSynchronizationAvailable() { return skippedItems == 0; }
    public List<String> getAbsentTitles() { return absentTitles; }
    public List<String> getExcludedByLimitTitles() { return excludedByLimitTitles; }
    public String getDestructivePlanFingerprint() { return destructivePlanFingerprint; }
}
