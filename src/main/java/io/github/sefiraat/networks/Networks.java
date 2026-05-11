package io.github.sefiraat.networks;

import com.balugaq.netex.utils.Converter;
import io.github.sefiraat.networks.commands.NetworksMain;
import io.github.sefiraat.networks.integrations.HudCallbacks;
import io.github.sefiraat.networks.integrations.NetheoPlants;
import io.github.sefiraat.networks.managers.ListenerManager;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.slimefun.NetworkResearches;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.slimefun.NetworksSlimefunItemStacks;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.utils.NetworkUtils;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.updater.BlobBuildUpdater;
import lombok.Getter;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Networks extends JavaPlugin implements SlimefunAddon {
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String CONFIG_BACKUPS_FOLDER_NAME = "config-backups";
    private static final String CONFIG_VERSION_KEY = "config-version";
    private static final String ADDON_RESEARCHES_ENABLED_KEY = "options.addon-researches";
    private static final String LEGACY_ADDON_RESEARCHES_ENABLED_KEY = "addon-researches-enabled";
    private static final DateTimeFormatter CONFIG_BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @Getter
    private static final Set<Location> controllersSet = new HashSet<>();

    private static Networks instance;

    private final String username;
    private final String repo;

    private ListenerManager listenerManager;
    private SupportedPluginManager supportedPluginManager;

    public Networks() {
        this.username = "Sefiraat";
        this.repo = "Networks";
    }

    @Nonnull
    public static PluginManager getPluginManager() {
        return Networks.getInstance().getServer().getPluginManager();
    }

    public static Networks getInstance() {
        return Networks.instance;
    }

    public static SupportedPluginManager getSupportedPluginManager() {
        return Networks.getInstance().supportedPluginManager;
    }

    public static ListenerManager getListenerManager() {
        return Networks.getInstance().listenerManager;
    }

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("########################################");
        getLogger().info("         Networks - By Sefiraat         ");
        getLogger().info("           Changed by mmmjjkx           ");
        getLogger().info("########################################");

        loadConfigWithMigration();
        tryUpdate();

        this.supportedPluginManager = new SupportedPluginManager();

        setupSlimefun();

        this.listenerManager = new ListenerManager();
        this.getCommand("networks").setExecutor(new NetworksMain());

        // Fix dupe bug which break the network controller data without player interaction
        Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    Set<Location> wrongs = new HashSet<>();
                    Set<Location> controllers = new HashSet<>(
                            NetworkController.getNetworks().keySet());
                    for (Location controller : controllers) {
                        if (!(BlockStorage.check(controller) instanceof NetworkController)) {
                            wrongs.add(controller);
                        }
                    }

                    for (Location wrong : wrongs) {
                        NetworkUtils.clearNetwork(wrong);
                    }
                },
                5, Slimefun.getTickerTask().getTickRate()
        );

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Location c : controllersSet) {
                if (BlockStorage.check(c) instanceof NetworkController) {
                    CraftBlock cb = ((CraftBlock) c.getBlock());
                    CraftWorld cw = ((CraftWorld) c.getWorld());

                    ServerLevel level = cw.getHandle();
                    LevelLightEngine ll = level.chunkSource.getLightEngine();

                    Bukkit.getScheduler().runTask(this, () -> {
                        cw.dropItemNaturally(cb.getLocation(), Converter.getItem(NetworksSlimefunItemStacks.NETWORK_CONTROLLER));

                        level.setBlock(cb.getPosition(), Blocks.AIR.defaultBlockState(), 0);
                        level.getMinecraftWorld().sendBlockUpdated(cb.getPosition(), cb.getNMS(), Blocks.AIR.defaultBlockState(), 3);
                        ll.checkBlock(cb.getPosition());
                    });

                    BlockStorage.clearBlockInfo(c);
                    NetworkUtils.clearNetwork(c);
                }
            }

            controllersSet.clear();
        }, 5, 10);

        setupMetrics();
    }

    public void tryUpdate() {
        if (getConfig().getBoolean("auto-update") && getPluginMeta().getVersion().startsWith("Dev")) {
            new BlobBuildUpdater(this, getFile(), "Networks", "Dev").start();
        }
    }

    public void setupSlimefun() {
        NetworkSlimefunItems.setup();
        if (getConfig().getBoolean(ADDON_RESEARCHES_ENABLED_KEY, false)) {
            NetworkResearches.setup(this);
        } else {
            getLogger().info("Addon researches are disabled in config.yml.");
        }
        if (supportedPluginManager.isNetheopoiesis()) {
            try {
                NetheoPlants.setup();
            } catch (NoClassDefFoundError e) {
                getLogger().severe("Netheopoiesis must be updated to meet Networks' requirements.");
            }
        }
        if (supportedPluginManager.isSlimeHud()) {
            try {
                HudCallbacks.setup();
            } catch (NoClassDefFoundError e) {
                getLogger().severe("SlimeHUD must be updated to meet Networks' requirements.");
            }
        }
    }

    public void setupMetrics() {
        final Metrics metrics = new Metrics(this, 13644);

        AdvancedPie networksChart = new AdvancedPie("networks", () -> {
            Map<String, Integer> networksMap = new HashMap<>();
            networksMap.put("Number of networks", NetworkController.getNetworks().size());
            return networksMap;
        });

        metrics.addCustomChart(networksChart);
    }

    private void loadConfigWithMigration() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().warning("Could not create plugin data folder, config backups may fail.");
        }

        File configFile = new File(dataFolder, CONFIG_FILE_NAME);
        YamlConfiguration bundledConfig = loadBundledConfig();
        int bundledVersion = bundledConfig.getInt(CONFIG_VERSION_KEY, -1);

        if (!configFile.exists()) {
            saveResource(CONFIG_FILE_NAME, false);
            reloadConfig();
            return;
        }

        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        int currentVersion = currentConfig.getInt(CONFIG_VERSION_KEY, -1);
        boolean needsMigration = currentVersion != bundledVersion
                || !currentConfig.contains(ADDON_RESEARCHES_ENABLED_KEY)
                || currentConfig.contains(LEGACY_ADDON_RESEARCHES_ENABLED_KEY);

        if (needsMigration) {
            try {
                File backupFile = backupConfig(configFile, currentVersion);
                YamlConfiguration mergedConfig = mergeConfig(currentConfig, bundledConfig);
                mergedConfig.save(configFile);
                getLogger().info(MessageFormat.format(
                        "Updated config.yml from version {0} to {1}. Backed up the original config to {2}.",
                        currentVersion,
                        bundledVersion,
                        backupFile.getPath()
                ));
            } catch (IOException e) {
                getLogger().severe("Could not back up config.yml, so the live config was left untouched.");
                getLogger().severe(e.getMessage());
            }
        }

        reloadConfig();
    }

    @Nonnull
    private YamlConfiguration loadBundledConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);

        try (InputStream inputStream = getResource(CONFIG_FILE_NAME)) {
            if (inputStream == null) {
                getLogger().warning("Bundled config.yml was not found, using an empty configuration.");
                return config;
            }

            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                StringWriter writer = new StringWriter();
                reader.transferTo(writer);
                String contents = writer.toString();
                config.loadFromString(contents);
                return config;
            }
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().warning("Could not read bundled config.yml, using an empty configuration.");
            return config;
        }
    }

    @Nonnull
    private File backupConfig(@Nonnull File configFile, int currentVersion) throws IOException {
        File backupsFolder = new File(getDataFolder(), CONFIG_BACKUPS_FOLDER_NAME);
        if (!backupsFolder.exists() && !backupsFolder.mkdirs()) {
            throw new IOException("Could not create config-backups folder.");
        }

        String backupName = MessageFormat.format(
                "config-v{0}-{1}.yml",
                currentVersion >= 0 ? currentVersion : "unknown",
                CONFIG_BACKUP_TIMESTAMP.format(LocalDateTime.now())
        );

        File backupFile = new File(backupsFolder, backupName);
        Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return backupFile;
    }

    @Nonnull
    private YamlConfiguration mergeConfig(@Nonnull FileConfiguration currentConfig, @Nonnull YamlConfiguration bundledConfig) {
        YamlConfiguration mergedConfig = new YamlConfiguration();
        mergedConfig.options().parseComments(true);

        try {
            mergedConfig.loadFromString(bundledConfig.saveToString());
        } catch (InvalidConfigurationException e) {
            getLogger().warning("Could not clone bundled config comments, continuing with value-only config merge.");
        }

        for (String path : currentConfig.getKeys(true)) {
            if (currentConfig.isConfigurationSection(path) || path.equals(CONFIG_VERSION_KEY) || path.equals(LEGACY_ADDON_RESEARCHES_ENABLED_KEY)) {
                continue;
            }

            mergedConfig.set(path, currentConfig.get(path));
        }

        if (currentConfig.contains(LEGACY_ADDON_RESEARCHES_ENABLED_KEY) && !currentConfig.contains(ADDON_RESEARCHES_ENABLED_KEY)) {
            mergedConfig.set(ADDON_RESEARCHES_ENABLED_KEY, currentConfig.getBoolean(LEGACY_ADDON_RESEARCHES_ENABLED_KEY));
        }

        mergedConfig.set(CONFIG_VERSION_KEY, bundledConfig.getInt(CONFIG_VERSION_KEY, -1));
        return mergedConfig;
    }

    @Nonnull
    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Nullable
    @Override
    public String getBugTrackerURL() {
        return MessageFormat.format("https://github.com/{0}/{1}/issues/", this.username, this.repo);
    }
}
