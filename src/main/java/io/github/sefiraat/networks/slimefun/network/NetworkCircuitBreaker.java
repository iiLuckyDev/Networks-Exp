package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.utils.ItemCreator;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
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
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkCircuitBreaker extends NetworkObject {

    private static final String POWERED_KEY = "powered";
    private static final Map<Location, Boolean> POWER_CACHE = new ConcurrentHashMap<>();

    private static final int[] BACKGROUND_SLOTS = new int[]{0, 1, 2, 3, 5, 6, 7, 8};
    private static final int STATUS_SLOT = 4;

    private static final ItemStack BACKGROUND = ItemCreator.create(Material.GRAY_STAINED_GLASS_PANE, " ");

    public NetworkCircuitBreaker(
            @Nonnull ItemGroup itemGroup,
            @Nonnull SlimefunItemStack item,
            @Nonnull RecipeType recipeType,
            ItemStack[] recipe
    ) {
        super(itemGroup, item, recipeType, recipe, NodeType.CIRCUIT_BREAKER);

        addItemHandler(
                new BlockTicker() {
                    @Override
                    public boolean isSynchronized() {
                        return true;
                    }

                    @Override
                    public void tick(Block block, SlimefunItem slimefunItem, Config config) {
                        final BlockMenu blockMenu = BlockStorage.getInventory(block);
                        updatePoweredState(block.getLocation());
                        if (blockMenu != null && blockMenu.hasViewer()) {
                            updateMenu(blockMenu);
                        }
                    }
                }
        );
    }

    public static boolean allowsTraversal(@Nonnull Location location) {
        return isPowered(location);
    }

    public static boolean isPowered(@Nonnull Location location) {
        final Boolean cachedState = POWER_CACHE.get(location);
        if (cachedState != null) {
            return cachedState;
        }

        final String storedState = BlockStorage.getLocationInfo(location, POWERED_KEY);
        if (storedState != null) {
            final boolean powered = Boolean.parseBoolean(storedState);
            POWER_CACHE.put(location.clone(), powered);
            return powered;
        }

        return readPoweredState(location);
    }

    private static boolean readPoweredState(@Nonnull Location location) {
        final Block block = location.getBlock();
        return block.isBlockPowered() || block.isBlockIndirectlyPowered() || block.getBlockPower() > 0;
    }

    public static boolean updatePoweredState(@Nonnull Location location) {
        final boolean previous = isPowered(location);
        final boolean current = readPoweredState(location);
        POWER_CACHE.put(location.clone(), current);
        BlockStorage.addBlockInfo(location, POWERED_KEY, Boolean.toString(current));
        return previous != current;
    }

    private static ItemStack getStatusStack(boolean powered) {
        return ItemCreator.create(
                powered ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                Theme.CLICK_INFO + "Circuit State: " + (powered ? Theme.SUCCESS + "Connected" : Theme.ERROR + "Disconnected"),
                Theme.PASSIVE + "Redstone Power: " + (powered ? Theme.SUCCESS + "Powered" : Theme.ERROR + "Unpowered"),
                "",
                Theme.PASSIVE + "Powered breakers pass the",
                Theme.PASSIVE + "network through this block.",
                Theme.PASSIVE + "Unpowered breakers stop it."
        );
    }

    private static ItemStack getStatusStack(@Nonnull Location location) {
        return getStatusStack(isPowered(location));
    }

    private void updateMenu(@Nonnull BlockMenu blockMenu) {
        final Location location = blockMenu.getLocation();
        blockMenu.replaceExistingItem(STATUS_SLOT, getStatusStack(location));
    }

    @Override
    protected void onPlace(@Nonnull BlockPlaceEvent event) {
        updatePoweredState(event.getBlockPlaced().getLocation());
    }

    @Override
    protected void onBreak(@Nonnull BlockBreakEvent event) {
        POWER_CACHE.remove(event.getBlock().getLocation());
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
                drawBackground(BACKGROUND, BACKGROUND_SLOTS);
                addItem(STATUS_SLOT, getStatusStack(false), (player, slot, item, action) -> false);
            }

            @Override
            public void newInstance(@Nonnull BlockMenu blockMenu, @Nonnull Block block) {
                updatePoweredState(blockMenu.getLocation());
                updateMenu(blockMenu);

                blockMenu.addMenuClickHandler(STATUS_SLOT, (player, slot, item, action) -> false);
            }

            @Override
            public boolean canOpen(@Nonnull Block block, @Nonnull Player player) {
                return NetworkSlimefunItems.NETWORK_CIRCUIT_BREAKER.canUse(player, false)
                        && Slimefun.getProtectionManager().hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
    }
}
