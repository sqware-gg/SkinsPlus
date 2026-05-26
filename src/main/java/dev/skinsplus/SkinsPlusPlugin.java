package dev.skinsplus;

import dev.skinsplus.command.SkinCommand;
import dev.skinsplus.skin.ConfigReferenceWriter;
import dev.skinsplus.skin.PlayerSkinSettingsStore;
import dev.skinsplus.skin.SkinApplier;
import dev.skinsplus.skin.SkinCache;
import dev.skinsplus.skin.SkinLogger;
import dev.skinsplus.skin.SkinMode;
import dev.skinsplus.skin.SkinResult;
import dev.skinsplus.skin.SkinService;
import dev.skinsplus.skin.SkinsPlusConfig;
import dev.skinsplus.skin.SkinsPlusConfigLoader;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class SkinsPlusPlugin extends JavaPlugin implements Listener {
    private static final int BSTATS_PLUGIN_ID = 31597;

    private PlayerSkinSettingsStore settingsStore;
    private SkinCache skinCache;
    private SkinService skinService;
    private SkinApplier skinApplier;
    private BukkitTask cacheMaintenanceTask;
    private volatile SkinsPlusConfig config;
    private SkinLogger skinLogger;

    @Override
    public void onEnable() {
        ConfigReferenceWriter.saveDefaultAndReferenceIfNeeded(this);
        skinLogger = createSkinLogger();
        loadConfig();
        startMetrics();
        skinApplier = new SkinApplier(this);
        loadServices();

        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommands();
        startCacheMaintenance();
    }

    private void startMetrics() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("auto_name_lookup", () -> enabledLabel(currentConfig().autoNameLookup())));
        metrics.addCustomChart(new SimplePie("fallback_skins_enabled", () -> enabledLabel(currentConfig().fallbackSkinsEnabled())));
        metrics.addCustomChart(new SimplePie("fallback_selection", () -> currentConfig().fallbackSelection()));
    }

    private String enabledLabel(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private void registerCommands() {
        SkinCommand skinCommand = new SkinCommand(this, settingsStore, skinService, skinApplier);
        for (String commandName : List.of("skin", "skins", "sr")) {
            PluginCommand command = getCommand(commandName);
            if (command != null) {
                command.setExecutor(skinCommand);
                command.setTabCompleter(skinCommand);
            }
        }
    }

    @Override
    public void onDisable() {
        stopCacheMaintenance();
        if (skinService != null) {
            skinService.shutdown();
        }
        if (settingsStore != null) {
            settingsStore.save();
        }
        if (skinCache != null) {
            skinCache.flushIfDirty();
        }
    }

    public void reloadPlugin() {
        stopCacheMaintenance();
        if (skinService != null) {
            skinService.shutdown();
        }
        if (skinCache != null) {
            skinCache.flushIfDirty();
        }
        ConfigReferenceWriter.saveDefaultAndReferenceIfNeeded(this);
        loadConfig();
        skinApplier = new SkinApplier(this);
        loadServices();
        registerCommands();
        startCacheMaintenance();

        if (config.reapplyOnReload()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshOnlinePlayer(player, true);
            }
        }
    }

    private void loadConfig() {
        config = SkinsPlusConfigLoader.load(getDataFolder().toPath().resolve("config.yml"), skinLogger);
    }

    private void loadServices() {
        settingsStore = new PlayerSkinSettingsStore(getDataFolder().toPath(), skinLogger);
        skinCache = new SkinCache(
                getDataFolder().toPath(),
                skinLogger,
                Duration.ofHours(config.skinCacheTtlHours()),
                Duration.ofMinutes(config.missingProfileCacheTtlMinutes())
        );
        skinService = new SkinService(settingsStore, skinCache, this::currentConfig, skinLogger);
    }

    public SkinsPlusConfig currentConfig() {
        return config == null ? SkinsPlusConfig.defaults() : config;
    }

    private SkinLogger createSkinLogger() {
        return new SkinLogger() {
            @Override
            public void warning(String message) {
                getLogger().warning(message);
            }

            @Override
            public void info(String message) {
                getLogger().info(message);
            }

            @Override
            public void fine(String message) {
                getLogger().fine(message);
            }
        };
    }

    private void startCacheMaintenance() {
        stopCacheMaintenance();
        SkinCache activeCache = skinCache;
        if (activeCache == null) {
            return;
        }
        cacheMaintenanceTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            activeCache.cleanupExpired();
            activeCache.flushIfDirty();
        }, 20L * 300L, 20L * 300L);
    }

    private void stopCacheMaintenance() {
        if (cacheMaintenanceTask != null) {
            cacheMaintenanceTask.cancel();
            cacheMaintenanceTask = null;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (skinService == null) {
            return;
        }
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        long timeout = currentConfig().loginLookupTimeoutSeconds();
        try {
            SkinResult result = skinService.resolveForLogin(event.getUniqueId(), event.getName())
                    .get(timeout, TimeUnit.SECONDS);
            result.property().ifPresent(property -> event.setPlayerProfile(
                    skinApplier.withSkinProperty(event.getPlayerProfile(), property)
            ));
        } catch (Exception exception) {
            getLogger().fine("Skipped login skin lookup for " + event.getName() + ": " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (settingsStore == null) {
            return;
        }
        Player player = event.getPlayer();
        Optional<SkinMode> storedMode = settingsStore.find(player.getUniqueId()).map(settings -> settings.mode());
        if (storedMode.isPresent() && storedMode.get() == SkinMode.NONE) {
            return;
        }

        // The profile is normally ready from pre-login. This fills gaps after cache failures or plugin reloads.
        Bukkit.getScheduler().runTaskLater(this, () -> refreshOnlinePlayer(player, true, false), 20L);
    }

    public CompletableFuture<SkinResult> refreshOnlinePlayer(Player player) {
        return refreshOnlinePlayer(player, true);
    }

    public CompletableFuture<SkinResult> refreshOnlinePlayer(Player player, boolean allowCached) {
        return refreshOnlinePlayer(player, allowCached, true);
    }

    private CompletableFuture<SkinResult> refreshOnlinePlayer(Player player, boolean allowCached, boolean applyEmptyResult) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        return skinService.resolveForOnline(playerId, playerName, allowCached)
                .thenApply(result -> {
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (player.isOnline() && (applyEmptyResult || result.property().isPresent())) {
                            skinApplier.apply(player, result.property());
                        }
                    });
                    return result;
                });
    }
}
