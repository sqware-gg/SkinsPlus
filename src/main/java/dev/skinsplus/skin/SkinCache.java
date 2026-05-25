package dev.skinsplus.skin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SkinCache {
    private static final int MAX_SKINS = 5_000;
    private static final int MAX_MISSING_PROFILES = 10_000;

    private final SkinLogger logger;
    private final Path file;
    private final Duration skinTtl;
    private final Duration missingTtl;
    private final ConcurrentMap<String, CachedSkin> skins = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MissingProfile> missingProfiles = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public SkinCache(Path dataFolder, SkinLogger logger, Duration skinTtl, Duration missingTtl) {
        this.logger = logger;
        this.file = dataFolder.resolve("skin-cache.yml");
        this.skinTtl = skinTtl;
        this.missingTtl = missingTtl;
        load();
    }

    public Optional<SkinProperty> findSkin(String name) {
        String key = normalize(name);
        long now = System.currentTimeMillis();
        CachedSkin cached = skins.get(key);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.expiresAt() <= now) {
            removeSkin(key, cached);
            return Optional.empty();
        }
        cached.touch(now);
        return Optional.of(cached.property());
    }

    public boolean isKnownMissing(String name) {
        String key = normalize(name);
        long now = System.currentTimeMillis();
        MissingProfile missingProfile = missingProfiles.get(key);
        if (missingProfile == null) {
            return false;
        }
        if (missingProfile.expiresAt() <= now) {
            removeMissingProfile(key, missingProfile);
            return false;
        }
        missingProfile.touch(now);
        return true;
    }

    public void putSkin(String name, SkinProperty property) {
        String key = normalize(name);
        long now = System.currentTimeMillis();
        missingProfiles.remove(key);
        skins.put(key, new CachedSkin(property, now, now, now + skinTtl.toMillis()));
        markDirty();
        enforceMaxEntries();
    }

    public void putMissing(String name) {
        String key = normalize(name);
        long now = System.currentTimeMillis();
        skins.remove(key);
        missingProfiles.put(key, new MissingProfile(now, now + missingTtl.toMillis()));
        markDirty();
        enforceMaxEntries();
    }

    public void invalidate(String name) {
        String key = normalize(name);
        boolean changed = skins.remove(key) != null;
        changed = missingProfiles.remove(key) != null || changed;
        if (changed) {
            markDirty();
        }
    }

    public int skinCount() {
        cleanupExpired();
        return skins.size();
    }

    public int missingProfileCount() {
        cleanupExpired();
        return missingProfiles.size();
    }

    public void flushIfDirty() {
        if (dirty.get()) {
            save();
        }
    }

    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (var entry : skins.entrySet()) {
            if (entry.getValue().expiresAt() <= now) {
                changed = skins.remove(entry.getKey(), entry.getValue()) || changed;
            }
        }
        for (var entry : missingProfiles.entrySet()) {
            if (entry.getValue().expiresAt() <= now) {
                changed = missingProfiles.remove(entry.getKey(), entry.getValue()) || changed;
            }
        }

        if (changed) {
            markDirty();
        }
    }

    public synchronized void save() {
        dirty.set(false);
        long now = System.currentTimeMillis();
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> skinSection = new LinkedHashMap<>();
        Map<String, Object> missingSection = new LinkedHashMap<>();

        for (var entry : skins.entrySet()) {
            if (entry.getValue().expiresAt() <= now) {
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("lookup-key", entry.getKey());
            data.put("value", entry.getValue().property().value());
            data.put("signature", entry.getValue().property().signature());
            data.put("created-at", entry.getValue().createdAt());
            data.put("last-accessed-at", entry.getValue().lastAccessedAt());
            data.put("expires-at", entry.getValue().expiresAt());
            skinSection.put(pathKey(entry.getKey()), data);
        }
        for (var entry : missingProfiles.entrySet()) {
            if (entry.getValue().expiresAt() <= now) {
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("lookup-key", entry.getKey());
            data.put("last-accessed-at", entry.getValue().lastAccessedAt());
            data.put("expires-at", entry.getValue().expiresAt());
            missingSection.put(pathKey(entry.getKey()), data);
        }

        root.put("skins", skinSection);
        root.put("missing", missingSection);
        YamlData.save(file, root, new SkinLogger() {
            @Override
            public void warning(String message) {
                markDirty();
                logger.warning(message);
            }
        }, "skin-cache.yml");
    }

    private void load() {
        skins.clear();
        missingProfiles.clear();
        Map<String, Object> root = YamlData.load(file, logger);
        Map<String, Object> skinSection = YamlData.section(root, "skins");
        for (Map.Entry<String, Object> entry : skinSection.entrySet()) {
            try {
                if (!(entry.getValue() instanceof Map<?, ?> rawData)) {
                    continue;
                }
                Map<String, Object> data = YamlData.stringMap(rawData);
                String lookupKey = YamlData.string(data, "lookup-key", entry.getKey());
                String value = YamlData.string(data, "value", null);
                String signature = YamlData.string(data, "signature", null);
                long expiresAt = YamlData.longValue(data, "expires-at", 0L);
                long createdAt = YamlData.longValue(data, "created-at", expiresAt - skinTtl.toMillis());
                long lastAccessedAt = YamlData.longValue(data, "last-accessed-at", createdAt);
                if (value != null && signature != null && expiresAt > System.currentTimeMillis()) {
                    skins.put(normalize(lookupKey), new CachedSkin(new SkinProperty(value, signature), createdAt, lastAccessedAt, expiresAt));
                }
            } catch (RuntimeException exception) {
                logger.warning("Ignoring invalid cached skin " + entry.getKey() + ": " + exception.getMessage());
            }
        }

        Map<String, Object> missingSection = YamlData.section(root, "missing");
        for (Map.Entry<String, Object> entry : missingSection.entrySet()) {
            try {
                if (!(entry.getValue() instanceof Map<?, ?> rawData)) {
                    continue;
                }
                Map<String, Object> data = YamlData.stringMap(rawData);
                String lookupKey = YamlData.string(data, "lookup-key", entry.getKey());
                long expiresAt = YamlData.longValue(data, "expires-at", 0L);
                long lastAccessedAt = YamlData.longValue(data, "last-accessed-at", expiresAt - missingTtl.toMillis());
                if (expiresAt > System.currentTimeMillis()) {
                    missingProfiles.put(normalize(lookupKey), new MissingProfile(lastAccessedAt, expiresAt));
                }
            } catch (RuntimeException exception) {
                logger.warning("Ignoring invalid missing-profile cache " + entry.getKey() + ": " + exception.getMessage());
            }
        }

        enforceMaxEntries();
        dirty.set(false);
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String pathKey(String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private void removeSkin(String key, CachedSkin cachedSkin) {
        if (skins.remove(key, cachedSkin)) {
            markDirty();
        }
    }

    private void removeMissingProfile(String key, MissingProfile missingProfile) {
        if (missingProfiles.remove(key, missingProfile)) {
            markDirty();
        }
    }

    private void enforceMaxEntries() {
        trimSkins();
        trimMissingProfiles();
    }

    private void trimSkins() {
        int excess = skins.size() - MAX_SKINS;
        if (excess <= 0) {
            return;
        }
        skins.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccessedAt()))
                .limit(excess)
                .forEach(entry -> removeSkin(entry.getKey(), entry.getValue()));
    }

    private void trimMissingProfiles() {
        int excess = missingProfiles.size() - MAX_MISSING_PROFILES;
        if (excess <= 0) {
            return;
        }
        missingProfiles.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccessedAt()))
                .limit(excess)
                .forEach(entry -> removeMissingProfile(entry.getKey(), entry.getValue()));
    }

    private void markDirty() {
        dirty.set(true);
    }

    private static final class CachedSkin {
        private final SkinProperty property;
        private final long createdAt;
        private volatile long lastAccessedAt;
        private final long expiresAt;

        private CachedSkin(SkinProperty property, long createdAt, long lastAccessedAt, long expiresAt) {
            this.property = property;
            this.createdAt = createdAt;
            this.lastAccessedAt = lastAccessedAt;
            this.expiresAt = expiresAt;
        }

        private SkinProperty property() {
            return property;
        }

        private long createdAt() {
            return createdAt;
        }

        private long lastAccessedAt() {
            return lastAccessedAt;
        }

        private long expiresAt() {
            return expiresAt;
        }

        private void touch(long timestamp) {
            lastAccessedAt = timestamp;
        }
    }

    private static final class MissingProfile {
        private volatile long lastAccessedAt;
        private final long expiresAt;

        private MissingProfile(long lastAccessedAt, long expiresAt) {
            this.lastAccessedAt = lastAccessedAt;
            this.expiresAt = expiresAt;
        }

        private long lastAccessedAt() {
            return lastAccessedAt;
        }

        private long expiresAt() {
            return expiresAt;
        }

        private void touch(long timestamp) {
            lastAccessedAt = timestamp;
        }
    }
}
