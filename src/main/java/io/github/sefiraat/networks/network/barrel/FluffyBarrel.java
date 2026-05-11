package io.github.sefiraat.networks.network.barrel;

import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FluffyBarrel extends BarrelIdentity {

    private static final String BARREL_CLASS_NAME = "io.ncbpfluffybear.fluffymachines.items.Barrel";
    private static final String UTILS_CLASS_NAME = "io.ncbpfluffybear.fluffymachines.utils.Utils";
    private static final int[] INPUT_SLOTS = new int[]{19, 20};
    private static final int[] OUTPUT_SLOTS = new int[]{24, 25};
    private static final int DISPLAY_SLOT = 31;

    private static boolean reflectionInitialized = false;
    private static boolean reflectionAvailable = false;
    private static boolean reflectionWarningLogged = false;

    private static @Nullable Class<?> barrelClass;
    private static @Nullable Method getStoredMethod;
    private static @Nullable Method setStoredMethod;
    private static @Nullable Method getStoredItemMethod;
    private static @Nullable Method getCapacityMethod;
    private static @Nullable Method updateMenuMethod;
    private static @Nullable Method keyItemMethod;

    private final Object barrelItem;

    private FluffyBarrel(@NotNull Location location, @Nullable ItemStack itemStack, int amount, @NotNull Object barrelItem) {
        super(location, itemStack, amount, BarrelType.FLUFFY);
        this.barrelItem = barrelItem;
    }

    public static boolean isSupportedSlimefunItem(@Nullable SlimefunItem slimefunItem) {
        initializeReflection();
        return reflectionAvailable && slimefunItem != null && barrelClass != null && barrelClass.isInstance(slimefunItem);
    }

    @Nullable
    public static FluffyBarrel getBarrel(@NotNull BlockMenu blockMenu, @NotNull SlimefunItem slimefunItem) {
        return getBarrel(blockMenu, slimefunItem, false);
    }

    @Nullable
    public static FluffyBarrel getBarrel(
            @NotNull BlockMenu blockMenu,
            @NotNull SlimefunItem slimefunItem,
            boolean includeEmpty
    ) {
        initializeReflection();
        if (!isSupportedSlimefunItem(slimefunItem)) {
            return null;
        }

        final Block block = blockMenu.getLocation().getBlock();
        try {
            final ItemStack representative = getRepresentativeItem(blockMenu, block, slimefunItem);
            final int totalAmount = getStoredAmount(slimefunItem, block) + getBufferedOutputAmount(blockMenu, representative);

            if (!includeEmpty && (representative == null || totalAmount <= 0)) {
                return null;
            }

            final ItemStack clone = representative == null ? null : representative.clone();
            if (clone != null) {
                clone.setAmount(1);
            }

            return new FluffyBarrel(blockMenu.getLocation(), clone, totalAmount, slimefunItem);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnReflectionFailure(e);
            return null;
        }
    }

    @Nullable
    @Override
    public ItemStack requestItem(@NotNull ItemRequest itemRequest) {
        if (itemRequest.getAmount() <= 0) {
            return null;
        }

        final BlockMenu blockMenu = BlockStorage.getInventory(this.getLocation());
        if (blockMenu == null) {
            return null;
        }

        final Block block = this.getLocation().getBlock();

        try {
            final ItemStack representative = getRepresentativeItem(blockMenu, block, this.barrelItem);
            if (representative == null) {
                return null;
            }

            final int capacity = getCapacity(this.barrelItem, block);
            int stored = getStoredAmount(this.barrelItem, block);
            int requested = itemRequest.getAmount();
            int extracted = 0;

            for (int outputSlot : OUTPUT_SLOTS) {
                if (requested <= 0) {
                    break;
                }

                final ItemStack output = blockMenu.getItemInSlot(outputSlot);
                if (output == null
                        || output.getType() == Material.AIR
                        || !StackUtils.itemsMatch(representative, output)) {
                    continue;
                }

                final int moved = Math.min(requested, output.getAmount());
                if (moved == output.getAmount()) {
                    blockMenu.replaceExistingItem(outputSlot, null);
                } else {
                    output.setAmount(output.getAmount() - moved);
                }

                requested -= moved;
                extracted += moved;
            }

            if (requested > 0 && stored > 0) {
                final int moved = Math.min(requested, stored);
                stored -= moved;
                setStoredAmount(this.barrelItem, block, stored);
                requested -= moved;
                extracted += moved;
            }

            if (extracted <= 0) {
                return null;
            }

            if (stored > 0) {
                setDisplayItem(blockMenu, representative);
            }

            updateMenu(this.barrelItem, block, blockMenu, capacity);
            blockMenu.markDirty();

            final ItemStack extractedStack = representative.clone();
            extractedStack.setAmount(extracted);
            return extractedStack;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnReflectionFailure(e);
            return null;
        }
    }

    @Override
    public void depositItemStack(ItemStack[] itemsToDeposit) {
        final BlockMenu blockMenu = BlockStorage.getInventory(this.getLocation());
        if (blockMenu == null) {
            return;
        }

        final Block block = this.getLocation().getBlock();

        try {
            int stored = getStoredAmount(this.barrelItem, block);
            final int capacity = getCapacity(this.barrelItem, block);
            ItemStack representative = getRepresentativeItem(blockMenu, block, this.barrelItem);
            int movedAny = 0;

            for (ItemStack itemToDeposit : itemsToDeposit) {
                if (itemToDeposit == null || itemToDeposit.getType() == Material.AIR || itemToDeposit.getAmount() <= 0) {
                    continue;
                }

                if (representative != null && !StackUtils.itemsMatch(representative, itemToDeposit)) {
                    continue;
                }

                if (representative == null) {
                    representative = itemToDeposit.clone();
                    representative.setAmount(1);
                }

                final int remainingCapacity = Math.max(capacity - stored, 0);
                if (remainingCapacity <= 0) {
                    break;
                }

                final int moved = Math.min(itemToDeposit.getAmount(), remainingCapacity);
                itemToDeposit.setAmount(itemToDeposit.getAmount() - moved);
                stored += moved;
                movedAny += moved;
            }

            if (movedAny <= 0 || representative == null) {
                return;
            }

            setStoredAmount(this.barrelItem, block, stored);
            setDisplayItem(blockMenu, representative);
            updateMenu(this.barrelItem, block, blockMenu, capacity);
            blockMenu.markDirty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnReflectionFailure(e);
        }
    }

    @Override
    public int getInputSlot() {
        return INPUT_SLOTS[0];
    }

    @Override
    public int getOutputSlot() {
        return OUTPUT_SLOTS[0];
    }

    private static synchronized void initializeReflection() {
        if (reflectionInitialized) {
            return;
        }

        reflectionInitialized = true;
        try {
            barrelClass = Class.forName(BARREL_CLASS_NAME);
            final Class<?> utilsClass = Class.forName(UTILS_CLASS_NAME);

            getStoredMethod = barrelClass.getMethod("getStored", Block.class);
            setStoredMethod = barrelClass.getMethod("setStored", Block.class, int.class);
            getStoredItemMethod = barrelClass.getMethod("getStoredItem", Block.class);
            getCapacityMethod = barrelClass.getMethod("getCapacity", Block.class);
            updateMenuMethod = barrelClass.getMethod("updateMenu", Block.class, BlockMenu.class, boolean.class, int.class);
            keyItemMethod = utilsClass.getMethod("keyItem", ItemStack.class);
            reflectionAvailable = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            reflectionAvailable = false;
            warnReflectionFailure(e);
        }
    }

    @Nullable
    private static ItemStack getRepresentativeItem(@NotNull BlockMenu blockMenu, @NotNull Block block, @NotNull Object barrelItem)
            throws InvocationTargetException, IllegalAccessException {
        final ItemStack storedItem = getStoredItem(barrelItem, block);
        if (storedItem != null && storedItem.getType() != Material.AIR && storedItem.getType() != Material.BARRIER) {
            return storedItem;
        }

        for (int outputSlot : OUTPUT_SLOTS) {
            final ItemStack output = blockMenu.getItemInSlot(outputSlot);
            if (output != null && output.getType() != Material.AIR) {
                return output.clone();
            }
        }

        return null;
    }

    private static int getBufferedOutputAmount(@NotNull BlockMenu blockMenu, @Nullable ItemStack representative) {
        int total = 0;
        for (int outputSlot : OUTPUT_SLOTS) {
            final ItemStack output = blockMenu.getItemInSlot(outputSlot);
            if (output == null || output.getType() == Material.AIR) {
                continue;
            }

            if (representative == null || StackUtils.itemsMatch(representative, output)) {
                total += output.getAmount();
            }
        }

        return total;
    }

    private static int getStoredAmount(@NotNull Object barrelItem, @NotNull Block block)
            throws InvocationTargetException, IllegalAccessException {
        return (int) invokeRequired(getStoredMethod, barrelItem, block);
    }

    private static void setStoredAmount(@NotNull Object barrelItem, @NotNull Block block, int amount)
            throws InvocationTargetException, IllegalAccessException {
        invokeRequired(setStoredMethod, barrelItem, block, amount);
    }

    @Nullable
    private static ItemStack getStoredItem(@NotNull Object barrelItem, @NotNull Block block)
            throws InvocationTargetException, IllegalAccessException {
        return (ItemStack) invokeRequired(getStoredItemMethod, barrelItem, block);
    }

    private static int getCapacity(@NotNull Object barrelItem, @NotNull Block block)
            throws InvocationTargetException, IllegalAccessException {
        return (int) invokeRequired(getCapacityMethod, barrelItem, block);
    }

    private static void updateMenu(
            @NotNull Object barrelItem,
            @NotNull Block block,
            @NotNull BlockMenu blockMenu,
            int capacity
    ) throws InvocationTargetException, IllegalAccessException {
        invokeRequired(updateMenuMethod, barrelItem, block, blockMenu, true, capacity);
    }

    private static void setDisplayItem(@NotNull BlockMenu blockMenu, @NotNull ItemStack representative) {
        final ItemStack displayItem = getKeyedItem(representative);
        displayItem.setAmount(1);
        blockMenu.replaceExistingItem(DISPLAY_SLOT, displayItem);
    }

    @NotNull
    private static ItemStack getKeyedItem(@NotNull ItemStack itemStack) {
        if (keyItemMethod != null) {
            try {
                return (ItemStack) keyItemMethod.invoke(null, itemStack.clone());
            } catch (IllegalAccessException | InvocationTargetException e) {
                warnReflectionFailure(e);
            }
        }

        final ItemStack clone = itemStack.clone();
        clone.setAmount(1);
        return clone;
    }

    private static Object invokeRequired(@Nullable Method method, @Nullable Object target, Object... args)
            throws InvocationTargetException, IllegalAccessException {
        if (method == null) {
            throw new IllegalStateException("Fluffy barrel reflection is not available");
        }

        return method.invoke(target, args);
    }

    private static void warnReflectionFailure(@NotNull Throwable throwable) {
        if (reflectionWarningLogged || Networks.getInstance() == null) {
            return;
        }

        reflectionWarningLogged = true;
        Networks.getInstance().getLogger().warning(
                "FluffyMachines barrel support is unavailable: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage()
        );
    }
}
