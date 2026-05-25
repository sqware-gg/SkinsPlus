package dev.skinsplus.command;

import dev.skinsplus.SkinsPlusPlugin;
import dev.skinsplus.skin.PlayerSkinSettings;
import dev.skinsplus.skin.PlayerSkinSettingsStore;
import dev.skinsplus.skin.SkinApplier;
import dev.skinsplus.skin.SkinLookupStatus;
import dev.skinsplus.skin.SkinMode;
import dev.skinsplus.skin.SkinResult;
import dev.skinsplus.skin.SkinService;
import dev.skinsplus.skin.TextureMetadata;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

public final class SkinCommand implements TabExecutor {
    private static final List<String> SKIN_SUBCOMMANDS = List.of("set", "auto", "clear", "reset", "default", "none", "off", "update", "refresh", "status", "list", "info", "random", "help");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("reload", "help");

    private final SkinsPlusPlugin plugin;
    private final PlayerSkinSettingsStore settingsStore;
    private final SkinService skinService;
    private final SkinApplier skinApplier;
    private final Random random = new Random();

    public SkinCommand(
            SkinsPlusPlugin plugin,
            PlayerSkinSettingsStore settingsStore,
            SkinService skinService,
            SkinApplier skinApplier
    ) {
        this.plugin = plugin;
        this.settingsStore = settingsStore;
        this.skinService = skinService;
        this.skinApplier = skinApplier;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sr")) {
            return handleAdmin(sender, args);
        }
        return handleSkin(sender, args);
    }

    private boolean handleSkin(CommandSender sender, String[] args) {
        Optional<Player> player = requirePlayer(sender);
        if (player.isEmpty()) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showStatus(player.get());
            skinHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> setNamedSkin(sender, player.get(), args);
            case "auto" -> setAuto(sender, player.get());
            case "clear", "reset", "default" -> clear(sender, player.get());
            case "none", "off", "disable" -> setNone(sender, player.get());
            case "update", "refresh" -> update(sender, player.get());
            case "status" -> showStatus(player.get());
            case "list" -> listAvailable(sender);
            case "info" -> skinInfo(sender, args);
            case "random" -> randomSkin(sender, player.get());
            default -> setSkin(sender, player.get(), args[0]);
        }
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            adminHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            return reload(sender);
        }

        adminHelp(sender);
        return true;
    }

    private void setNamedSkin(CommandSender sender, Player player, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /skin set <skinName>", NamedTextColor.RED));
            return;
        }
        setSkin(sender, player, args[1]);
    }

    private void setSkin(CommandSender sender, Player player, String skinName) {
        if (!require(sender, "skinsplus.command.skin", "skinsrestorer.command.set")) {
            return;
        }

        PlayerSkinSettings next = new PlayerSkinSettings(player.getUniqueId(), player.getName(), SkinMode.MOJANG, skinName, System.currentTimeMillis());
        sender.sendMessage(Component.text("Looking up skin " + skinName + "...", NamedTextColor.YELLOW));
        applySetting(sender, player, next, true);
    }

    private void setAuto(CommandSender sender, Player player) {
        if (!require(sender, "skinsplus.command.skin", "skinsrestorer.command.set")) {
            return;
        }

        PlayerSkinSettings next = new PlayerSkinSettings(player.getUniqueId(), player.getName(), SkinMode.AUTO, null, System.currentTimeMillis());
        sender.sendMessage(Component.text("Restoring automatic skin...", NamedTextColor.YELLOW));
        applySetting(sender, player, next, false);
    }

    private void setNone(CommandSender sender, Player player) {
        if (!require(sender, "skinsplus.command.skin", "skinsrestorer.command.clear")) {
            return;
        }

        settingsStore.set(new PlayerSkinSettings(player.getUniqueId(), player.getName(), SkinMode.NONE, null, System.currentTimeMillis()));
        skinApplier.apply(player, Optional.empty());
        sender.sendMessage(Component.text("Skin disabled.", NamedTextColor.YELLOW));
    }

    private void clear(CommandSender sender, Player player) {
        if (!require(sender, "skinsplus.command.skin", "skinsrestorer.command.clear")) {
            return;
        }

        settingsStore.remove(player.getUniqueId());
        sender.sendMessage(Component.text("Resetting skin...", NamedTextColor.YELLOW));
        plugin.refreshOnlinePlayer(player, true)
                .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> sendResult(sender, player, result, false)))
                .exceptionally(exception -> {
                    sendFailure(sender, "Skin reset failed.");
                    return null;
                });
    }

    private void update(CommandSender sender, Player player) {
        if (!require(sender, "skinsplus.command.skin", "skinsrestorer.command.update")) {
            return;
        }

        sender.sendMessage(Component.text("Refreshing skin...", NamedTextColor.YELLOW));
        plugin.refreshOnlinePlayer(player, false)
                .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> sendResult(sender, player, result, false)))
                .exceptionally(exception -> {
                    sendFailure(sender, "Skin update failed.");
                    return null;
                });
    }

    private void listAvailable(CommandSender sender) {
        if (!require(sender, "skinsplus.command.skin")) {
            return;
        }

        List<String> fallbackSkins = availableSkinNames();
        sender.sendMessage(Component.text("Configured fallback skins:", NamedTextColor.AQUA));
        sender.sendMessage(Component.text(fallbackSkins.isEmpty() ? "none" : String.join(", ", fallbackSkins), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("You can use any valid player name: /skin set <name>", NamedTextColor.DARK_GRAY));
    }

    private void skinInfo(CommandSender sender, String[] args) {
        if (!require(sender, "skinsplus.command.skin")) {
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /skin info <skinName>", NamedTextColor.RED));
            return;
        }
        describeSkin(sender, args[1]);
    }

    private void randomSkin(CommandSender sender, Player player) {
        if (!require(sender, "skinsplus.command.skin", "skinsrestorer.command.set")) {
            return;
        }

        List<String> choices = availableSkinNames().stream()
                .filter(skinService::isValidMinecraftName)
                .toList();
        if (choices.isEmpty()) {
            sender.sendMessage(Component.text("No fallback skins are configured.", NamedTextColor.YELLOW));
            return;
        }
        setSkin(sender, player, choices.get(random.nextInt(choices.size())));
    }

    private boolean reload(CommandSender sender) {
        if (!require(sender, "skinsplus.admin", "skinsrestorer.admin", "skinsrestorer.admincommand")) {
            return true;
        }
        plugin.reloadPlugin();
        sender.sendMessage(Component.text("SkinsPlus reloaded.", NamedTextColor.GREEN));
        return true;
    }

    private void applySetting(CommandSender sender, Player player, PlayerSkinSettings next, boolean rollbackOnFailure) {
        Optional<PlayerSkinSettings> previous = settingsStore.find(player.getUniqueId());
        settingsStore.set(next);
        skinService.resolveForOnline(player.getUniqueId(), player.getName(), true)
                .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean failed = rollbackOnFailure && result.status() != SkinLookupStatus.APPLIED;
                    if (failed) {
                        previous.ifPresentOrElse(settingsStore::set, () -> settingsStore.remove(player.getUniqueId()));
                    } else {
                        skinApplier.apply(player, result.property());
                    }
                    sendResult(sender, player, result, failed);
                }))
                .exceptionally(exception -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        previous.ifPresentOrElse(settingsStore::set, () -> settingsStore.remove(player.getUniqueId()));
                        sender.sendMessage(Component.text("Skin lookup failed.", NamedTextColor.RED));
                    });
                    return null;
                });
    }

    private void sendResult(CommandSender sender, Player player, SkinResult result, boolean rolledBack) {
        switch (result.status()) {
            case APPLIED -> {
                sender.sendMessage(Component.text("Skin applied from " + result.sourceName() + " (" + textureSummary(result) + ").", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("If you do not see it immediately, rejoin once.", NamedTextColor.GRAY));
            }
            case DISABLED -> sender.sendMessage(Component.text("Skin disabled.", NamedTextColor.YELLOW));
            case MISSING_PROFILE -> sender.sendMessage(Component.text(rolledBack
                    ? "No skin exists for " + result.sourceName() + ". Settings were not changed."
                    : "No skin exists for " + result.sourceName() + ".", NamedTextColor.YELLOW));
            case ERROR -> sender.sendMessage(Component.text("Skin lookup failed: " + result.message(), NamedTextColor.RED));
        }
    }

    private void sendFailure(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(message, NamedTextColor.RED)));
    }

    private Optional<Player> requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        sender.sendMessage(Component.text("Only players can use skin commands.", NamedTextColor.RED));
        return Optional.empty();
    }

    private boolean require(CommandSender sender, String... permissions) {
        if (hasAny(sender, permissions)) {
            return true;
        }
        sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
        return false;
    }

    private boolean hasAny(CommandSender sender, String... permissions) {
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        return sender.hasPermission("skinsplus.admin");
    }

    private void showStatus(Player player) {
        PlayerSkinSettings settings = settingsStore.findOrDefault(player.getUniqueId(), player.getName());
        player.sendMessage(Component.text("Current skin: " + skinKey(settings), NamedTextColor.AQUA));
    }

    private String skinKey(PlayerSkinSettings settings) {
        if (settings.mode() == SkinMode.AUTO) {
            return "auto";
        }
        if (settings.skinName() == null || settings.skinName().isBlank()) {
            return settings.mode().name().toLowerCase(Locale.ROOT);
        }
        return settings.skinName();
    }

    private void skinHelp(CommandSender sender) {
        sender.sendMessage(Component.text("/skin set <name>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/skin auto", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/skin clear", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/skin none", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/skin update", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/skin status | list | info <name> | random", NamedTextColor.GRAY));
    }

    private void adminHelp(CommandSender sender) {
        sender.sendMessage(Component.text("/sr reload", NamedTextColor.GRAY));
    }

    private void describeSkin(CommandSender sender, String skinName) {
        sender.sendMessage(Component.text("Looking up skin " + skinName + "...", NamedTextColor.YELLOW));
        skinService.resolveInput(skinName, true)
                .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (result.property().isEmpty()) {
                        sender.sendMessage(Component.text("No skin exists for " + skinName + ".", NamedTextColor.YELLOW));
                        return;
                    }
                    TextureMetadata metadata = TextureMetadata.from(result.property().get());
                    sender.sendMessage(Component.text("Skin: " + skinName, NamedTextColor.AQUA));
                    sender.sendMessage(Component.text("Textures: " + metadata.summary(), NamedTextColor.GRAY));
                }))
                .exceptionally(exception -> {
                    sendFailure(sender, "Skin lookup failed.");
                    return null;
                });
    }

    private String textureSummary(SkinResult result) {
        return result.property()
                .map(TextureMetadata::from)
                .map(TextureMetadata::summary)
                .orElse("cape=no");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> source = command.getName().equalsIgnoreCase("sr") ? ADMIN_SUBCOMMANDS : SKIN_SUBCOMMANDS;
            return source.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("info"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return availableSkinNames().stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private List<String> availableSkinNames() {
        return new ArrayList<>(plugin.currentConfig().fallbackSkins());
    }
}
