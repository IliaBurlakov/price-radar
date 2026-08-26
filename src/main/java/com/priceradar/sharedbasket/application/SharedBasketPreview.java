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
    private final int missingTracked;
    private final int freeSlots;
    private final int addableItems;
    private final int syncTargetItems;
    private final List<String> missingTitles;

    public SharedBasketPreview(
            UUID importId, int foundItems, int readyItems, int skippedItems,
            int alreadyTracked, int newItems, int missingTracked, int freeSlots,
            int addableItems, int syncTargetItems, List<String> missingTitles
    ) {
        this.importId = importId;
        this.foundItems = foundItems;
        this.readyItems = readyItems;
        this.skippedItems = skippedItems;
        this.alreadyTracked = alreadyTracked;
        this.newItems = newItems;
        this.missingTracked = missingTracked;
        this.freeSlots = freeSlots;
        this.addableItems = addableItems;
        this.syncTargetItems = syncTargetItems;
        this.missingTitles = List.copyOf(missingTitles);
    }

    public UUID getImportId() { return importId; }
    public int getFoundItems() { return foundItems; }
    public int getReadyItems() { return readyItems; }
    public int getSkippedItems() { return skippedItems; }
    public int getAlreadyTracked() { return alreadyTracked; }
    public int getNewItems() { return newItems; }
    public int getMissingTracked() { return missingTracked; }
    public int getFreeSlots() { return freeSlots; }
    public int getAddableItems() { return addableItems; }
    public int getAddSkippedByLimit() { return Math.max(0, newItems - addableItems); }
    public int getSyncTargetItems() { return syncTargetItems; }
    public int getSyncSkippedByLimit() { return Math.max(0, readyItems - syncTargetItems); }
    public List<String> getMissingTitles() { return missingTitles; }
}
