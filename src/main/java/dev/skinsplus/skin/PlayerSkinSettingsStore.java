package dev.skinsplus.skin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerSkinSettingsStore {
    private final SkinLogger logger;
    private final Path file;
    private final ConcurrentMap<UUID, PlayerSkinSettings> settings = new ConcurrentHashMap<>();

    public PlayerSkinSettingsStore(Path dataFolder, SkinLogger logger) {
        this.logger = logger;
        this.file = dataFolder.resolve("players.yml");
        load();
    }

    public Optional<PlayerSkinSettings> find(UUID playerId) {
        return Optional.ofNullable(settings.get(playerId));
    }

    public PlayerSkinSettings findOrDefault(UUID playerId, String playerName) {
        return find(playerId).orElseGet(() -> new PlayerSkinSettings(playerId, playerName, SkinMode.AUTO, null, 0L));
    }

    public void set(PlayerSkinSettings playerSettings) {
        settings.put(playerSettings.playerId(), playerSettings);
        save();
    }

    public void remove(UUID playerId) {
        settings.remove(playerId);
        save();
    }

    public int size() {
        return settings.size();
    }

    private void load() {
        settings.clear();
        Map<String, Object> root = YamlData.load(file, logger);
        Map<String, Object> players = YamlData.section(root, "players");
        for (Map.Entry<String, Object> entry : players.entrySet()) {
            try {
                UUID playerId = UUID.fromString(entry.getKey());
                if (!(entry.getValue() instanceof Map<?, ?> rawData)) {
                    continue;
                }
                Map<String, Object> data = YamlData.stringMap(rawData);
                SkinMode mode = readMode(YamlData.string(data, "mode", "AUTO"), YamlData.string(data, "skin-name", null));
                settings.put(playerId, new PlayerSkinSettings(
                        playerId,
                        YamlData.string(data, "last-known-name", ""),
                        mode,
                        YamlData.string(data, "skin-name", null),
                        YamlData.longValue(data, "updated-at", 0L)
                ));
            } catch (RuntimeException exception) {
                logger.warning("Ignoring invalid player skin settings entry " + entry.getKey() + ": " + exception.getMessage());
            }
        }
    }

    public void save() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> players = new LinkedHashMap<>();
        for (PlayerSkinSettings playerSettings : settings.values()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("last-known-name", playerSettings.lastKnownName());
            data.put("mode", playerSettings.mode().name());
            data.put("skin-name", playerSettings.skinName());
            data.put("updated-at", playerSettings.updatedAt());
            players.put(playerSettings.playerId().toString(), data);
        }
        root.put("players", players);
        YamlData.save(file, root, logger, "players.yml");
    }

    private SkinMode readMode(String rawMode, String skinName) {
        try {
            String normalized = rawMode == null ? "AUTO" : rawMode.toUpperCase(Locale.ROOT);
            if (normalized.equals("CUSTOM")) {
                return skinName == null || skinName.isBlank() ? SkinMode.AUTO : SkinMode.MOJANG;
            }
            return SkinMode.valueOf(normalized);
        } catch (RuntimeException exception) {
            return SkinMode.AUTO;
        }
    }
}
