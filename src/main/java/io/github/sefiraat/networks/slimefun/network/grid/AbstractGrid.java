package io.github.sefiraat.networks.slimefun.network.grid;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.*;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.sefiraat.networks.utils.ItemCreator;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.text.MessageFormat;
import java.util.*;

public abstract class AbstractGrid extends NetworkObject {

    private static final ItemStack BLANK_SLOT_STACK = ItemCreator.create(
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            " "
    );

    private static final ItemStack PAGE_PREVIOUS_STACK = ItemCreator.create(
            Material.RED_STAINED_GLASS_PANE,
            Theme.CLICK_INFO.getColor() + "Previous Page"
    );

    private static final ItemStack PAGE_NEXT_STACK = ItemCreator.create(
            Material.RED_STAINED_GLASS_PANE,
            Theme.CLICK_INFO.getColor() + "Next Page"
    );

    private static final ItemStack FILTER_STACK = ItemCreator.create(
            Material.NAME_TAG,
            Theme.CLICK_INFO.getColor() + "Set Filter (Right Click to Clear)"
    );

    private static final Comparator<Map.Entry<ItemStack, Integer>> ALPHABETICAL_SORT = Comparator.comparing(
            itemStackIntegerEntry -> {
                ItemStack itemStack = itemStackIntegerEntry.getKey();
                SlimefunItem slimefunItem = SlimefunItem.getByItem(itemStack);
                if (slimefunItem != null) {
                    return ChatColor.stripColor(slimefunItem.getItemName());
                } else {
                    ItemMeta itemMeta = itemStackIntegerEntry.getKey().getItemMeta();
                    return itemMeta.hasDisplayName()
                            ? ChatColor.stripColor(itemMeta.getDisplayName())
                            : itemStackIntegerEntry.getKey().getType().name();
                }
            }
    );

    private static final Comparator<Map.Entry<ItemStack, Integer>> COUNT_LOW_TO_HIGH_SORT =
            Map.Entry.<ItemStack, Integer>comparingByValue().thenComparing(ALPHABETICAL_SORT);
    private static final Comparator<Map.Entry<ItemStack, Integer>> COUNT_HIGH_TO_LOW_SORT =
            Map.Entry.<ItemStack, Integer>comparingByValue().reversed().thenComparing(ALPHABETICAL_SORT);

    private final ItemSetting<Integer> tickRate;

    protected AbstractGrid(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.GRID);

        this.getSlotsToDrop().add(getInputSlot());

        this.tickRate = new IntRangeSetting(this, "tick_rate", 1, 1, 10);
        addItemSetting(this.tickRate);

        addItemHandler(
                new BlockTicker() {

                    private int tick = 1;

                    @Override
                    public boolean isSynchronized() {
                        return false;
                    }

                    @Override
                    public void tick(Block block, SlimefunItem item, Config data) {
                        if (tick <= 1) {
                            final BlockMenu blockMenu = BlockStorage.getInventory(block);
                            addToRegistry(block);
                            tryAddItem(blockMenu);
                            updateDisplay(blockMenu);
                        }
                    }

                    @Override
                    public void uniqueTick() {
                        tick = tick <= 1 ? tickRate.getValue() : tick - 1;
                    }
                }
        );
    }

    @Nonnull
    private static List<String> getLoreAddition(int amount) {
        final MessageFormat format = new MessageFormat("{0}Amount: {1}{2}", Locale.ROOT);
        return List.of(
                "",
                format.format(new Object[]{Theme.CLICK_INFO.getColor(), Theme.PASSIVE.getColor(), amount}, new StringBuffer(), null).toString()
        );
    }

    protected void tryAddItem(@Nonnull BlockMenu blockMenu) {
        final ItemStack itemStack = blockMenu.getItemInSlot(getInputSlot());

        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }

        final NetworkRoot root = getActiveRoot(blockMenu);
        if (root == null) {
            return;
        }

        root.addItemStack0(blockMenu.getLocation(), itemStack);
    }

    protected void updateDisplay(@Nonnull BlockMenu blockMenu) {
        // No viewer - lets not bother updating
        if (!blockMenu.hasViewer()) {
            return;
        }

        final NetworkRoot root = getActiveRoot(blockMenu);

        if (root == null) {
            clearDisplay(blockMenu);
            return;
        }

        final GridCache gridCache = getCacheMap().get(blockMenu.getLocation().clone());
        final List<Map.Entry<ItemStack, Integer>> entries = getEntries(root, gridCache);
        final int pages = (int) Math.ceil(entries.size() / (double) getDisplaySlots().length) - 1;

        gridCache.setMaxPages(pages);

        // Set everything to blank and return if there are no pages (no items)
        if (pages < 0) {
            clearDisplay(blockMenu);
            return;
        }

        // Reset selected page if it no longer exists due to items being removed
        if (gridCache.getPage() > pages) {
            gridCache.setPage(0);
        }

        final int start = gridCache.getPage() * getDisplaySlots().length;
        final int end = Math.min(start + getDisplaySlots().length, entries.size());
        final List<Map.Entry<ItemStack, Integer>> validEntries = entries.subList(start, end);

        getCacheMap().put(blockMenu.getLocation(), gridCache);

        for (int i = 0; i < getDisplaySlots().length; i++) {
            if (validEntries.size() > i) {
                final Map.Entry<ItemStack, Integer> entry = validEntries.get(i);
                final ItemStack displayStack = entry.getKey().clone();
                final ItemMeta itemMeta = displayStack.getItemMeta();
                List<String> lore = itemMeta.getLore();

                if (lore == null) {
                    lore = getLoreAddition(entry.getValue());
                } else {
                    lore.addAll(getLoreAddition(entry.getValue()));
                }

                itemMeta.setLore(lore);
                displayStack.setItemMeta(itemMeta);
                blockMenu.replaceExistingItem(getDisplaySlots()[i], displayStack);
                blockMenu.addMenuClickHandler(getDisplaySlots()[i], (player, slot, item, action) -> {
                    retrieveItem(player, item, action, blockMenu);
                    return false;
                });
            } else {
                blockMenu.replaceExistingItem(getDisplaySlots()[i], BLANK_SLOT_STACK);
                blockMenu.addMenuClickHandler(getDisplaySlots()[i], (p, slot, item, action) -> false);
            }
        }
    }

    @Nullable
    protected NetworkRoot getActiveRoot(@Nonnull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());

        final NetworkRoot root = definition == null ? null : definition.getRoot();

        if (root == null) {
            return null;
        }

        final Location controller = root.getController();

        if (controller == null
                || NetworkController.getNetworks().get(controller) != root
                || !root.getNodeLocations().contains(blockMenu.getLocation())) {
            return null;
        }

        return root;
    }

    protected void clearDisplay(BlockMenu blockMenu) {
        for (int displaySlot : getDisplaySlots()) {
            blockMenu.replaceExistingItem(displaySlot, BLANK_SLOT_STACK);
            blockMenu.addMenuClickHandler(displaySlot, (p, slot, item, action) -> false);
        }
    }

    @Nonnull
    protected List<Map.Entry<ItemStack, Integer>> getEntries(@Nonnull NetworkRoot networkRoot, @Nonnull GridCache cache) {
        final String filter = cache.getFilter();
        final GridCache.SortOrder sortOrder = cache.getSortOrder();

        return cache.getStableEntries(networkRoot.getAllNetworkItems()).entrySet().stream()
                .filter(entry -> {
                    if (filter == null) {
                        return true;
                    }

                    final ItemStack itemStack = entry.getKey();
                    String name = itemStack.getType().name().toLowerCase(Locale.ROOT);
                    if (itemStack.hasItemMeta()) {
                        final ItemMeta itemMeta = itemStack.getItemMeta();
                        if (itemMeta.hasDisplayName()) {
                            name = ChatColor.stripColor(itemMeta.getDisplayName().toLowerCase(Locale.ROOT));
                        }
                    }
                    return name.contains(filter);
                })
                .sorted(getComparator(sortOrder))
                .toList();
    }

    @Nonnull
    private Comparator<Map.Entry<ItemStack, Integer>> getComparator(@Nonnull GridCache.SortOrder sortOrder) {
        return switch (sortOrder) {
            case COUNT_LOW_TO_HIGH -> COUNT_LOW_TO_HIGH_SORT;
            case COUNT_HIGH_TO_LOW, NUMBER -> COUNT_HIGH_TO_LOW_SORT;
            case ALPHABETICAL -> ALPHABETICAL_SORT;
        };
    }

    protected boolean setFilter(@Nonnull Player player, @Nonnull GridCache gridCache, @Nonnull ClickAction action) {
        if (action.isRightClicked()) {
            gridCache.setFilter(null);
        } else {
            player.closeInventory();
            player.sendMessage(Theme.WARNING + "Type what you would like to filter this grid to");
            ChatUtils.awaitInput(player, s -> {
                if (s.isBlank()) {
                    return;
                }
                gridCache.setFilter(s.toLowerCase(Locale.ROOT));
                player.sendMessage(Theme.SUCCESS + "Filter applied");
            });
        }
        return false;
    }

    @ParametersAreNonnullByDefault
    protected void retrieveItem(Player player, @Nullable ItemStack itemStack, ClickAction action, BlockMenu blockMenu) {
        // Todo Item can be null here. No idea how - investigate later
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }

        final ItemStack clone = itemStack.clone();
        final ItemMeta cloneMeta = clone.getItemMeta();
        final List<String> cloneLore = cloneMeta.getLore();

        cloneLore.remove(cloneLore.size() - 1);
        cloneLore.remove(cloneLore.size() - 1);
        cloneMeta.setLore(cloneLore);
        clone.setItemMeta(cloneMeta);
        int amount = 1;

        if (action.isRightClicked()) {
            amount = clone.getMaxStackSize();
        }

        final GridItemRequest request = new GridItemRequest(clone, amount, player);

        if (action.isShiftClicked()) {
            addToInventory(player, request, blockMenu);
        } else {
            addToCursor(player, request, action, blockMenu);
        }

        updateDisplay(blockMenu);
    }

    @ParametersAreNonnullByDefault
    private void addToInventory(Player player, GridItemRequest request, BlockMenu menu) {
        final NetworkRoot root = getActiveRoot(menu);

        if (root == null) {
            clearDisplay(menu);
            return;
        }

        ItemStack requestingStack = root.getItemStack0(menu.getLocation(), request);

        if (requestingStack == null) {
            return;
        }

        HashMap<Integer, ItemStack> remnant = player.getInventory().addItem(requestingStack);
        requestingStack = remnant.values().stream().findFirst().orElse(null);
        if (requestingStack != null) {
            root.addItemStack0(player.getLocation(), requestingStack);
        }
    }

    @ParametersAreNonnullByDefault
    private void addToCursor(Player player, GridItemRequest request, ClickAction action, BlockMenu menu) {
        final ItemStack cursor = player.getItemOnCursor();

        // Quickly check if the cursor has an item and if we can add more to it
        if (cursor.getType() != Material.AIR && !canAddMore(action, cursor, request)) {
            return;
        }

        final NetworkRoot root = getActiveRoot(menu);

        if (root == null) {
            clearDisplay(menu);
            return;
        }

        ItemStack requestingStack = root.getItemStack0(menu.getLocation(), request);
        setCursor(player, cursor, requestingStack);
    }

    private void setCursor(Player player, ItemStack cursor, ItemStack requestingStack) {
        if (requestingStack != null) {
            if (cursor.getType() != Material.AIR) {
                requestingStack.setAmount(cursor.getAmount() + 1);
            }
            player.setItemOnCursor(requestingStack);
        }
    }

    private boolean canAddMore(@Nonnull ClickAction action, @Nonnull ItemStack cursor, @Nonnull GridItemRequest request) {
        return !action.isRightClicked()
                && request.getAmount() == 1
                && cursor.getAmount() < cursor.getMaxStackSize()
                && StackUtils.itemsMatch(request, cursor, true);
    }

    @Override
    public void postRegister() {
        getPreset();
    }

    @Nonnull
    protected abstract BlockMenuPreset getPreset();

    @Nonnull
    protected abstract Map<Location, GridCache> getCacheMap();

    protected abstract int[] getBackgroundSlots();

    protected abstract int[] getDisplaySlots();

    protected abstract int getInputSlot();

    protected abstract int getChangeSort();

    protected abstract int getPagePrevious();

    protected abstract int getPageNext();

    protected abstract int getFilterSlot();

    protected ItemStack getBlankSlotStack() {
        return BLANK_SLOT_STACK;
    }

    protected ItemStack getPagePreviousStack() {
        return PAGE_PREVIOUS_STACK;
    }

    protected ItemStack getPageNextStack() {
        return PAGE_NEXT_STACK;
    }

    protected ItemStack getChangeSortStack(@Nonnull GridCache gridCache) {
        return ItemCreator.create(
                Material.BLUE_STAINED_GLASS_PANE,
                Theme.CLICK_INFO.getColor() + "Sort: " + gridCache.getSortOrder().getDisplayName(),
                Theme.CLICK_INFO + "Click: " + Theme.PASSIVE + "Switch to " + gridCache.getSortOrder().getNext().getDisplayName()
        );
    }

    protected ItemStack getFilterStack() {
        return FILTER_STACK;
    }

    public void receiveItem(
            Player player, ItemStack itemStack, ClickAction action, BlockMenu blockMenu) {
        final NetworkRoot root = getActiveRoot(blockMenu);

        if (root == null) {
            clearDisplay(blockMenu);
            blockMenu.close();
            return;
        }

        receiveItem(root, player, itemStack, action, blockMenu);
    }

    @SuppressWarnings({"unused"})
    public void receiveItem(
            NetworkRoot root,
            Player player,
            @Nullable ItemStack itemStack,
            ClickAction action,
            BlockMenu blockMenu) {
        if (getActiveRoot(blockMenu) != root) {
            clearDisplay(blockMenu);
            blockMenu.close();
            return;
        }

        if (itemStack != null && itemStack.getType() != Material.AIR) {
            root.addItemStack0(blockMenu.getLocation(), itemStack);
        }
    }
}
