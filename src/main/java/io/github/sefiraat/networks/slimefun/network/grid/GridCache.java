package io.github.sefiraat.networks.slimefun.network.grid;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class GridCache {

    private static final int MISSING_ENTRY_GRACE_UPDATES = 2;

    @Setter
    @Getter
    private volatile int page;
    @Setter
    @Getter
    private volatile int maxPages;
    @Nonnull
    private volatile SortOrder sortOrder;
    @Nullable
    private volatile String filter;
    @Nonnull
    private final Map<ItemStack, StableEntry> stableEntries = new HashMap<>();

    public GridCache(int page, int maxPages, @Nonnull SortOrder sortOrder) {
        this.page = page;
        this.maxPages = maxPages;
        this.sortOrder = sortOrder;
    }

    @Nonnull
    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(@Nonnull SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Nullable
    public String getFilter() {
        return filter;
    }

    public void setFilter(@Nullable String filter) {
        this.filter = filter;
    }

    @Nonnull
    public synchronized Map<ItemStack, Integer> getStableEntries(@Nonnull Map<ItemStack, Integer> currentEntries) {
        final Map<ItemStack, Integer> entries = new HashMap<>(currentEntries);

        for (Iterator<Map.Entry<ItemStack, StableEntry>> iterator = stableEntries.entrySet().iterator(); iterator.hasNext();) {
            final Map.Entry<ItemStack, StableEntry> stableEntry = iterator.next();
            final Integer currentAmount = currentEntries.get(stableEntry.getKey());

            if (currentAmount != null) {
                stableEntry.getValue().setAmount(currentAmount);
                stableEntry.getValue().setMissedUpdates(0);
                continue;
            }

            stableEntry.getValue().incrementMissedUpdates();
            if (stableEntry.getValue().getMissedUpdates() > MISSING_ENTRY_GRACE_UPDATES) {
                iterator.remove();
            } else {
                entries.put(stableEntry.getKey(), stableEntry.getValue().getAmount());
            }
        }

        for (Map.Entry<ItemStack, Integer> currentEntry : currentEntries.entrySet()) {
            stableEntries.computeIfAbsent(
                    currentEntry.getKey().clone(),
                    ignored -> new StableEntry(currentEntry.getValue())
            );
        }

        return entries;
    }

    public enum SortOrder {
        COUNT_LOW_TO_HIGH("Count - Low to High"),
        COUNT_HIGH_TO_LOW("Count - High to Low"),
        ALPHABETICAL("Name - A to Z"),
        NUMBER("Count - High to Low");

        private final String displayName;

        SortOrder(@Nonnull String displayName) {
            this.displayName = displayName;
        }

        @Nonnull
        public String getDisplayName() {
            return displayName;
        }

        @Nonnull
        public SortOrder getNext() {
            return switch (this) {
                case COUNT_LOW_TO_HIGH -> COUNT_HIGH_TO_LOW;
                case COUNT_HIGH_TO_LOW, NUMBER -> ALPHABETICAL;
                case ALPHABETICAL -> COUNT_LOW_TO_HIGH;
            };
        }
    }

    private static final class StableEntry {
        private int amount;
        private int missedUpdates;

        private StableEntry(int amount) {
            this.amount = amount;
        }

        private int getAmount() {
            return amount;
        }

        private void setAmount(int amount) {
            this.amount = amount;
        }

        private int getMissedUpdates() {
            return missedUpdates;
        }

        private void setMissedUpdates(int missedUpdates) {
            this.missedUpdates = missedUpdates;
        }

        private void incrementMissedUpdates() {
            this.missedUpdates++;
        }
    }
}
