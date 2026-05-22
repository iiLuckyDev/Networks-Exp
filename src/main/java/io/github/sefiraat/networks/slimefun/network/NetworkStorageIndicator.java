package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.utils.ItemCreator;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkStorageIndicator extends NetworkObject {

    private static final String THRESHOLD_KEY = "threshold";
    private static final String ACTIVE_KEY = "active";
    private static final int DEFAULT_THRESHOLD = 50;
    private static final int MIN_THRESHOLD = 0;
    private static final int MAX_THRESHOLD = Integer.MAX_VALUE;

    private static final int STATUS_SLOT = 4;
    private static final int DECREASE_LARGE_SLOT = 10;
    private static final int DECREASE_SMALL_SLOT = 12;
    private static final int INCREASE_SMALL_SLOT = 14;
    private static final int INCREASE_LARGE_SLOT = 16;
    private static final int[] BACKGROUND_SLOTS = new int[]{0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 13, 15, 17};

    private static final ItemStack BACKGROUND = ItemCreator.create(Material.GRAY_STAINED_GLASS_PANE, " ");
    private static final ItemStack DECREASE_LARGE = ItemCreator.create(Material.RED_STAINED_GLASS_PANE, Theme.CLICK_INFO + "-100");
    private static final ItemStack DECREASE_SMALL = ItemCreator.create(Material.ORANGE_STAINED_GLASS_PANE, Theme.CLICK_INFO + "-10");
    private static final ItemStack INCREASE_SMALL = ItemCreator.create(Material.LIME_STAINED_GLASS_PANE, Theme.CLICK_INFO + "+10");
    private static final ItemStack INCREASE_LARGE = ItemCreator.create(Material.GREEN_STAINED_GLASS_PANE, Theme.CLICK_INFO + "+100");

    private static final Map<Location, Integer> TICK_COUNTERS = new ConcurrentHashMap<>();
    private static final Map<Location, Boolean> ACTIVE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Location, Boolean> PENDING_STATES = new ConcurrentHashMap<>();
    private static final Map<Location, Integer> PENDING_STATE_COUNTS = new ConcurrentHashMap<>();

    private final ItemSetting<Integer> checkInterval;
    private final ItemSetting<Integer> stateChangeChecks;

    public NetworkStorageIndicator(
            @Nonnull ItemGroup itemGroup,
            @Nonnull SlimefunItemStack item,
            @Nonnull RecipeType recipeType,
            ItemStack[] recipe
    ) {
        super(itemGroup, item, recipeType, recipe, NodeType.STORAGE_INDICATOR);

        this.checkInterval = new IntRangeSetting(this, "check_interval", 1, 20, 600);
        this.stateChangeChecks = new IntRangeSetting(this, "state_change_checks", 1, 2, 10);
        addItemSetting(this.checkInterval, this.stateChangeChecks);

        addItemHandler(
                new BlockTicker() {
                    @Override
                    public boolean isSynchronized() {
                        return true;
                    }

                    @Override
                    public void tick(Block block, SlimefunItem slimefunItem, Config config) {
                        final Location location = block.getLocation();
                        final int nextTick = TICK_COUNTERS.merge(location, 1, Integer::sum);
                        if (ACTIVE_CACHE.containsKey(location) && nextTick < checkInterval.getValue()) {
                            return;
                        }

                        TICK_COUNTERS.put(location, 0);
                        updateIndicator(block, BlockStorage.getInventory(block), true);
                    }
                }
        );
    }

    private void updateIndicator(@Nonnull Block block, BlockMenu blockMenu) {
        updateIndicator(block, blockMenu, false);
    }

    private void updateIndicator(@Nonnull Block block, BlockMenu blockMenu, boolean debounceStateChanges) {
        final Location location = block.getLocation();
        final boolean active = shouldBeActive(location, getThreshold(location));

        if (debounceStateChanges && !isConfirmedState(location, active)) {
            if (blockMenu != null && blockMenu.hasViewer()) {
                updateMenu(blockMenu);
            }
            return;
        }

        setActive(block, active);
        if (blockMenu != null && blockMenu.hasViewer()) {
            updateMenu(blockMenu);
        }
    }

    private boolean isConfirmedState(@Nonnull Location location, boolean active) {
        final boolean currentState = ACTIVE_CACHE.getOrDefault(
                location,
                Boolean.parseBoolean(BlockStorage.getLocationInfo(location, ACTIVE_KEY))
        );

        if (currentState == active) {
            clearPendingState(location);
            return true;
        }

        final Boolean pendingState = PENDING_STATES.get(location);
        final int pendingChecks;

        if (pendingState != null && pendingState == active) {
            pendingChecks = PENDING_STATE_COUNTS.merge(location, 1, Integer::sum);
        } else {
            PENDING_STATES.put(location.clone(), active);
            PENDING_STATE_COUNTS.put(location.clone(), 1);
            pendingChecks = 1;
        }

        if (pendingChecks < stateChangeChecks.getValue()) {
            return false;
        }

        clearPendingState(location);
        return true;
    }

    private static void clearPendingState(@Nonnull Location location) {
        PENDING_STATES.remove(location);
        PENDING_STATE_COUNTS.remove(location);
    }

    private boolean shouldBeActive(@Nonnull Location location, int threshold) {
        if (threshold <= MIN_THRESHOLD) {
            return false;
        }

        final NetworkRoot root = getActiveRoot(location);
        if (root == null) {
            return false;
        }

        for (BarrelIdentity storage : root.getObservedStorages(true)) {
            if (storage.getAmount() < threshold) {
                return true;
            }
        }

        for (BlockMenu cellMenu : root.getCellMenus()) {
            if (getStoredAmount(cellMenu) < threshold) {
                return true;
            }
        }

        return false;
    }

    private int getStoredAmount(@Nonnull BlockMenu blockMenu) {
        int storedAmount = 0;
        for (int slot : NetworkCell.SLOTS) {
            final ItemStack itemStack = blockMenu.getItemInSlot(slot);
            if (itemStack != null && !itemStack.getType().isAir()) {
                storedAmount += itemStack.getAmount();
            }
        }
        return storedAmount;
    }

    private NetworkRoot getActiveRoot(@Nonnull Location location) {
        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(location);
        final NetworkRoot root = definition == null ? null : definition.getRoot();

        if (root == null
                || root.getController() == null
                || NetworkController.getNetworks().get(root.getController()) != root
                || !root.getNodeLocations().contains(location)) {
            return null;
        }

        return root;
    }

    private static int getThreshold(@Nonnull Location location) {
        final String value = BlockStorage.getLocationInfo(location, THRESHOLD_KEY);
        if (value == null) {
            return DEFAULT_THRESHOLD;
        }

        try {
            return Math.max(MIN_THRESHOLD, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return DEFAULT_THRESHOLD;
        }
    }

    private void setThreshold(@Nonnull BlockMenu blockMenu, int threshold) {
        final Location location = blockMenu.getLocation();
        final int boundedThreshold = (int) Math.max(MIN_THRESHOLD, Math.min((long) MAX_THRESHOLD, (long) threshold));
        BlockStorage.addBlockInfo(location, THRESHOLD_KEY, Integer.toString(boundedThreshold));
        blockMenu.markDirty();
        updateIndicator(blockMenu.getBlock(), blockMenu);
    }

    private void adjustThreshold(@Nonnull BlockMenu blockMenu, int amount) {
        final long threshold = (long) getThreshold(blockMenu.getLocation()) + amount;
        setThreshold(blockMenu, (int) Math.max(MIN_THRESHOLD, Math.min((long) MAX_THRESHOLD, threshold)));
    }

    private void setActive(@Nonnull Block block, boolean active) {
        final Location location = block.getLocation();
        final Boolean previous = ACTIVE_CACHE.put(location.clone(), active);
        final boolean storedPrevious = Boolean.parseBoolean(BlockStorage.getLocationInfo(location, ACTIVE_KEY));

        if (previous != null && previous == active && storedPrevious == active) {
            return;
        }

        BlockStorage.addBlockInfo(location, ACTIVE_KEY, Boolean.toString(active));

        final BlockData blockData = block.getBlockData();
        if (blockData instanceof Lightable lightable && lightable.isLit() != active) {
            lightable.setLit(active);
            block.setBlockData(lightable, false);
        }
    }

    private ItemStack getStatusStack(boolean active, int threshold) {
        return ItemCreator.create(
                active ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                Theme.CLICK_INFO + "Storage Indicator: " + (active ? Theme.SUCCESS + "On" : Theme.ERROR + "Off"),
                Theme.PASSIVE + "Threshold: " + Theme.CLICK_INFO + threshold + Theme.PASSIVE + " items",
                "",
                Theme.PASSIVE + "Turns on when any monitored",
                Theme.PASSIVE + "storage has fewer than",
                Theme.PASSIVE + "the configured item count.",
                "",
                Theme.PASSIVE + "Use the threshold buttons",
                Theme.PASSIVE + "to adjust the item count."
        );
    }

    private ItemStack getStatusStack(@Nonnull Location location) {
        final boolean active = ACTIVE_CACHE.getOrDefault(location, Boolean.parseBoolean(BlockStorage.getLocationInfo(location, ACTIVE_KEY)));
        return getStatusStack(active, getThreshold(location));
    }

    private void updateMenu(@Nonnull BlockMenu blockMenu) {
        blockMenu.replaceExistingItem(STATUS_SLOT, getStatusStack(blockMenu.getLocation()));
    }

    @Override
    protected void onPlace(@Nonnull BlockPlaceEvent event) {
        final Location location = event.getBlockPlaced().getLocation();
        if (BlockStorage.getLocationInfo(location, THRESHOLD_KEY) == null) {
            BlockStorage.addBlockInfo(location, THRESHOLD_KEY, Integer.toString(DEFAULT_THRESHOLD));
        }
        clearPendingState(location);
        setActive(event.getBlockPlaced(), false);
    }

    @Override
    protected void onBreak(@Nonnull BlockBreakEvent event) {
        final Location location = event.getBlock().getLocation();
        TICK_COUNTERS.remove(location);
        ACTIVE_CACHE.remove(location);
        clearPendingState(location);
        super.onBreak(event);
    }

    @Override
    public boolean runSync() {
        return true;
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {
            @Override
            public void init() {
                setSize(18);
                drawBackground(BACKGROUND, BACKGROUND_SLOTS);
                addItem(STATUS_SLOT, getStatusStack(false, DEFAULT_THRESHOLD), (player, slot, item, action) -> false);
                addItem(DECREASE_LARGE_SLOT, DECREASE_LARGE, (player, slot, item, action) -> false);
                addItem(DECREASE_SMALL_SLOT, DECREASE_SMALL, (player, slot, item, action) -> false);
                addItem(INCREASE_SMALL_SLOT, INCREASE_SMALL, (player, slot, item, action) -> false);
                addItem(INCREASE_LARGE_SLOT, INCREASE_LARGE, (player, slot, item, action) -> false);
            }

            @Override
            public void newInstance(@Nonnull BlockMenu blockMenu, @Nonnull Block block) {
                updateIndicator(block, blockMenu);
                blockMenu.addMenuClickHandler(STATUS_SLOT, (player, slot, item, action) -> false);
                blockMenu.addMenuClickHandler(DECREASE_LARGE_SLOT, (player, slot, item, action) -> {
                    adjustThreshold(blockMenu, -100);
                    return false;
                });
                blockMenu.addMenuClickHandler(DECREASE_SMALL_SLOT, (player, slot, item, action) -> {
                    adjustThreshold(blockMenu, -10);
                    return false;
                });
                blockMenu.addMenuClickHandler(INCREASE_SMALL_SLOT, (player, slot, item, action) -> {
                    adjustThreshold(blockMenu, 10);
                    return false;
                });
                blockMenu.addMenuClickHandler(INCREASE_LARGE_SLOT, (player, slot, item, action) -> {
                    adjustThreshold(blockMenu, 100);
                    return false;
                });
            }

            @Override
            public boolean canOpen(@Nonnull Block block, @Nonnull Player player) {
                return NetworkSlimefunItems.NETWORK_STORAGE_INDICATOR.canUse(player, false)
                        && Slimefun.getProtectionManager().hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
    }
}
