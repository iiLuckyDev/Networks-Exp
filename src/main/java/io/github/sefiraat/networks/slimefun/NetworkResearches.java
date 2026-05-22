package io.github.sefiraat.networks.slimefun;

import io.github.sefiraat.networks.Networks;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;

import javax.annotation.Nonnull;
import java.util.Locale;

public final class NetworkResearches {

    private static final int RESEARCH_ID_BASE = 842100;

    private NetworkResearches() {
    }

    public static void setup(@Nonnull Networks plugin) {
        int nextId = RESEARCH_ID_BASE;

        nextId = registerTier(plugin, nextId, 4,
                NetworkSlimefunItems.SYNTHETIC_EMERALD_SHARD,
                NetworkSlimefunItems.NETWORK_BRIDGE_ORANGE,
                NetworkSlimefunItems.NETWORK_BRIDGE_MAGENTA,
                NetworkSlimefunItems.NETWORK_BRIDGE_LIGHT_BLUE,
                NetworkSlimefunItems.NETWORK_BRIDGE_YELLOW,
                NetworkSlimefunItems.NETWORK_BRIDGE_LIME,
                NetworkSlimefunItems.NETWORK_BRIDGE_PINK,
                NetworkSlimefunItems.NETWORK_BRIDGE_GRAY,
                NetworkSlimefunItems.NETWORK_BRIDGE_CYAN,
                NetworkSlimefunItems.NETWORK_BRIDGE_PURPLE,
                NetworkSlimefunItems.NETWORK_BRIDGE_BLUE,
                NetworkSlimefunItems.NETWORK_BRIDGE_BROWN,
                NetworkSlimefunItems.NETWORK_BRIDGE_GREEN,
                NetworkSlimefunItems.NETWORK_BRIDGE_RED,
                NetworkSlimefunItems.NETWORK_BRIDGE_BLACK
        );

        nextId = registerTier(plugin, nextId, 6,
                NetworkSlimefunItems.OPTIC_GLASS,
                NetworkSlimefunItems.NETWORK_BRIDGE,
                NetworkSlimefunItems.NETWORK_CRAYON
        );

        nextId = registerTier(plugin, nextId, 8,
                NetworkSlimefunItems.OPTIC_CABLE,
                NetworkSlimefunItems.CRAFTING_BLUEPRINT
        );

        nextId = registerTier(plugin, nextId, 10,
                NetworkSlimefunItems.OPTIC_STAR,
                NetworkSlimefunItems.NETWORK_CONTROLLER,
                NetworkSlimefunItems.NETWORK_CIRCUIT_BREAKER,
                NetworkSlimefunItems.NETWORK_STORAGE_INDICATOR,
                NetworkSlimefunItems.NETWORK_MONITOR,
                NetworkSlimefunItems.NETWORK_GRID,
                NetworkSlimefunItems.NETWORK_CELL,
                NetworkSlimefunItems.NETWORK_PROBE
        );

        nextId = registerTier(plugin, nextId, 12,
                NetworkSlimefunItems.NETWORK_IMPORT,
                NetworkSlimefunItems.NETWORK_EXPORT,
                NetworkSlimefunItems.NETWORK_PURGER
        );

        nextId = registerTier(plugin, nextId, 14,
                NetworkSlimefunItems.NETWORK_GRABBER,
                NetworkSlimefunItems.NETWORK_PUSHER,
                NetworkSlimefunItems.NETWORK_VANILLA_GRABBER,
                NetworkSlimefunItems.NETWORK_VANILLA_PUSHER,
                NetworkSlimefunItems.NETWORK_POWER_DISPLAY,
                NetworkSlimefunItems.NETWORK_CAPACITOR_1,
                NetworkSlimefunItems.NETWORK_POWER_OUTLET_1,
                NetworkSlimefunItems.NETWORK_CRAFTING_GRID,
                NetworkSlimefunItems.NETWORK_QUANTUM_WORKBENCH,
                NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_1
        );

        nextId = registerTier(plugin, nextId, 16,
                NetworkSlimefunItems.NETWORK_BEST_PUSHER,
                NetworkSlimefunItems.NETWORK_CONTROL_X,
                NetworkSlimefunItems.NETWORK_CONTROL_V,
                NetworkSlimefunItems.NETWORK_VACUUM,
                NetworkSlimefunItems.NETWORK_CAPACITOR_2,
                NetworkSlimefunItems.NETWORK_POWER_OUTLET_2,
                NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_2
        );

        nextId = registerTier(plugin, nextId, 18,
                NetworkSlimefunItems.NETWORK_CAPACITOR_3,
                NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_3,
                NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_4
        );

        nextId = registerTier(plugin, nextId, 20,
                NetworkSlimefunItems.NETWORK_CAPACITOR_4,
                NetworkSlimefunItems.RADIOACTIVE_OPTIC_STAR,
                NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_5
        );

        nextId = registerTier(plugin, nextId, 22,
                NetworkSlimefunItems.SHRINKING_BASE,
                NetworkSlimefunItems.SIMPLE_NANOBOTS,
                NetworkSlimefunItems.NETWORK_GREEDY_BLOCK,
                NetworkSlimefunItems.NETWORK_RECIPE_ENCODER,
                NetworkSlimefunItems.NETWORK_RAKE_1,
                NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_6
        );

        nextId = registerTier(plugin, nextId, 24,
                NetworkSlimefunItems.ADVANCED_NANOBOTS,
                NetworkSlimefunItems.AI_CORE,
                NetworkSlimefunItems.NETWORK_AUTO_CRAFTER,
                NetworkSlimefunItems.NETWORK_WIRELESS_RECEIVER,
                NetworkSlimefunItems.NETWORK_CONFIGURATOR,
                NetworkSlimefunItems.NETWORK_REMOTE,
                NetworkSlimefunItems.NETWORK_RAKE_2,
                NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_7
        );

        nextId = registerTier(plugin, nextId, 26,
                NetworkSlimefunItems.EMPOWERED_AI_CORE,
                NetworkSlimefunItems.NETWORK_AUTO_CRAFTER_WITHHOLDING,
                NetworkSlimefunItems.NETWORK_REMOTE_EMPOWERED,
                NetworkSlimefunItems.NETWORK_WIRELESS_TRANSMITTER,
                NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_8
        );

        nextId = registerTier(plugin, nextId, 28,
                NetworkSlimefunItems.PRISTINE_AI_CORE,
                NetworkSlimefunItems.NETWORK_WIRELESS_CONFIGURATOR,
                NetworkSlimefunItems.NETWORK_REMOTE_PRISTINE,
                NetworkSlimefunItems.NETWORK_RAKE_3
        );

        registerTier(plugin, nextId, 30,
                NetworkSlimefunItems.INTERDIMENSIONAL_PRESENCE,
                NetworkSlimefunItems.NETWORK_REMOTE_ULTIMATE
        );
    }

    private static int registerTier(@Nonnull Networks plugin, int nextId, int cost, @Nonnull SlimefunItem... items) {
        int currentId = nextId;
        for (SlimefunItem item : items) {
            registerItem(plugin, currentId++, cost, item);
        }
        return currentId;
    }

    private static void registerItem(@Nonnull Networks plugin, int id, int cost, @Nonnull SlimefunItem item) {
        String key = "unlock_" + item.getId().toLowerCase(Locale.ROOT);
        String name = ChatColor.stripColor(item.getItemName());

        Research research = new Research(new NamespacedKey(plugin, key), id, name, cost);
        research.addItems(item);
        research.register();
    }
}
