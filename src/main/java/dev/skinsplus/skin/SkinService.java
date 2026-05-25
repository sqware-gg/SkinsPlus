package dev.skinsplus.skin;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkinService {
    private static final Pattern MINECRAFT_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern JSON_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TEXTURES_PROPERTY_OBJECT = Pattern.compile("\\{[^{}]*\"name\"\\s*:\\s*\"textures\"[^{}]*}", Pattern.DOTALL);
    private static final Pattern JSON_VALUE = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_SIGNATURE = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");

    private final PlayerSkinSettingsStore settingsStore;
    private final SkinCache skinCache;
    private final Supplier<SkinsPlusConfig> configSupplier;
    private final SkinLogger logger;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final Map<String, Object> lookupLocks = new ConcurrentHashMap<>();

    public SkinService(
            PlayerSkinSettingsStore settingsStore,
            SkinCache skinCache,
            Supplier<SkinsPlusConfig> configSupplier,
            SkinLogger logger
    ) {
        this.settingsStore = settingsStore;
        this.skinCache = skinCache;
        this.configSupplier = configSupplier;
        this.logger = logger;
        this.executorService = Executors.newFixedThreadPool(4, new SkinThreadFactory());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .executor(executorService)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CompletableFuture<SkinResult> resolveForLogin(UUID playerId, String playerName) {
        return CompletableFuture.supplyAsync(() -> resolve(playerId, playerName, true), executorService);
    }

    public CompletableFuture<SkinResult> resolveForOnline(UUID playerId, String playerName, boolean allowCached) {
        return CompletableFuture.supplyAsync(() -> resolve(playerId, playerName, allowCached), executorService);
    }

    public CompletableFuture<SkinResult> resolveInput(String input, boolean allowCached) {
        return CompletableFuture.supplyAsync(() -> resolveInputSync(input, allowCached), executorService);
    }

    public boolean isValidMinecraftName(String name) {
        return name != null && MINECRAFT_NAME.matcher(name).matches();
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    private SkinResult resolve(UUID playerId, String playerName, boolean allowCached) {
        PlayerSkinSettings settings = settingsStore.findOrDefault(playerId, playerName);
        if (settings.mode() == SkinMode.NONE) {
            return SkinResult.disabled(playerName);
        }

        boolean automaticSelection = settings.mode() == SkinMode.AUTO;
        String sourceName = automaticSelection ? playerName : settings.skinName();

        if (!isValidMinecraftName(sourceName)) {
            return automaticSelection
                    ? resolveFallbackSkin(playerId, playerName, allowCached)
                    : SkinResult.missingProfile(sourceName == null ? playerName : sourceName);
        }

        try {
            if (automaticSelection && autoNameLookupEnabled()) {
                Optional<SkinProperty> property = lookupSkin(sourceName, allowCached);
                if (property.isPresent()) {
                    return SkinResult.applied(sourceName, property.get());
                }
            } else if (!automaticSelection) {
                Optional<SkinProperty> property = lookupSkin(sourceName, allowCached);
                return property
                        .map(skinProperty -> SkinResult.applied(sourceName, skinProperty))
                        .orElseGet(() -> SkinResult.missingProfile(sourceName));
            }
            return automaticSelection ? resolveFallbackSkin(playerId, playerName, allowCached) : SkinResult.missingProfile(sourceName);
        } catch (RuntimeException exception) {
            return automaticSelection ? resolveFallbackSkin(playerId, playerName, allowCached) : SkinResult.error(sourceName, exception.getMessage());
        }
    }

    private SkinResult resolveInputSync(String input, boolean allowCached) {
        if (input == null || input.isBlank()) {
            return SkinResult.error("", "Skin input is required");
        }

        if (!isValidMinecraftName(input)) {
            return SkinResult.missingProfile(input);
        }

        try {
            return lookupSkin(input, allowCached)
                    .map(skinProperty -> SkinResult.applied(input, skinProperty))
                    .orElseGet(() -> SkinResult.missingProfile(input));
        } catch (RuntimeException exception) {
            return SkinResult.error(input, exception.getMessage());
        }
    }

    private Optional<SkinProperty> lookupSkin(String sourceName, boolean allowCached) {
        String lockKey = sourceName.toLowerCase(Locale.ROOT);
        Object lock = lookupLocks.computeIfAbsent(lockKey, ignored -> new Object());
        try {
            synchronized (lock) {
                return lookupSkinLocked(sourceName, allowCached);
            }
        } finally {
            lookupLocks.remove(lockKey, lock);
        }
    }

    private Optional<SkinProperty> lookupSkinLocked(String sourceName, boolean allowCached) {
        if (allowCached) {
            Optional<SkinProperty> cached = skinCache.findSkin(sourceName);
            if (cached.isPresent()) {
                return cached;
            }
            if (skinCache.isKnownMissing(sourceName)) {
                return Optional.empty();
            }
        } else {
            skinCache.invalidate(sourceName);
        }

        Optional<MojangProfile> profile = fetchProfile(sourceName);
        if (profile.isEmpty()) {
            skinCache.putMissing(sourceName);
            return Optional.empty();
        }

        SkinProperty property = fetchTextures(profile.get());
        skinCache.putSkin(profile.get().name(), property);
        if (!profile.get().name().equalsIgnoreCase(sourceName)) {
            skinCache.putSkin(sourceName, property);
        }
        return Optional.of(property);
    }

    private boolean autoNameLookupEnabled() {
        return configSupplier.get().autoNameLookup();
    }

    private SkinResult resolveFallbackSkin(UUID playerId, String playerName, boolean allowCached) {
        SkinsPlusConfig config = configSupplier.get();
        if (!config.fallbackSkinsEnabled()) {
            return SkinResult.missingProfile(playerName);
        }

        List<String> fallbackSkins = new ArrayList<>(config.fallbackSkins());
        if (fallbackSkins.isEmpty()) {
            fallbackSkins.addAll(List.of("Steve", "Alex", "Notch", "jeb_", "Dinnerbone"));
        }

        int start = fallbackStartIndex(playerId, playerName, fallbackSkins.size());
        for (int offset = 0; offset < fallbackSkins.size(); offset++) {
            String fallbackName = fallbackSkins.get((start + offset) % fallbackSkins.size());
            if (!isValidMinecraftName(fallbackName)) {
                continue;
            }
            try {
                Optional<SkinProperty> property = lookupSkin(fallbackName, allowCached);
                if (property.isPresent()) {
                    return SkinResult.applied(fallbackName, property.get());
                }
            } catch (RuntimeException exception) {
                logger.fine("Skipped fallback skin " + fallbackName + ": " + exception.getMessage());
            }
        }

        return SkinResult.missingProfile(playerName);
    }

    private int fallbackStartIndex(UUID playerId, String playerName, int size) {
        String selection = configSupplier.get().fallbackSelection();
        if (selection.equalsIgnoreCase("first")) {
            return 0;
        }
        return Math.floorMod((playerId + ":" + playerName.toLowerCase()).hashCode(), size);
    }

    private Optional<MojangProfile> fetchProfile(String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new CompletionException("Profile lookup returned HTTP " + response.statusCode(), null);
        }

        String id = match(JSON_ID, response.body()).orElseThrow(() -> new CompletionException("Profile response did not include an id", null));
        String returnedName = match(JSON_NAME, response.body()).orElse(name);
        return Optional.of(new MojangProfile(id, returnedName));
    }

    private SkinProperty fetchTextures(MojangProfile profile) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + profile.id() + "?unsigned=false"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new CompletionException("Session lookup returned HTTP " + response.statusCode(), null);
        }

        Matcher matcher = TEXTURES_PROPERTY_OBJECT.matcher(response.body());
        if (!matcher.find()) {
            throw new CompletionException("Session response did not include signed textures", null);
        }
        String propertyObject = matcher.group();
        String value = match(JSON_VALUE, propertyObject).orElseThrow(() -> new CompletionException("Textures response did not include a value", null));
        String signature = match(JSON_SIGNATURE, propertyObject).orElseThrow(() -> new CompletionException("Textures response did not include a signature", null));
        return new SkinProperty(value, signature);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new CompletionException("Skin request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Skin request was interrupted", exception);
        }
    }

    private Optional<String> match(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Optional.of(unescapeJson(matcher.group(1))) : Optional.empty();
    }

    private String unescapeJson(String value) {
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private record MojangProfile(String id, String name) {
    }

    private static final class SkinThreadFactory implements ThreadFactory {
        private int threadNumber;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "SkinsPlus Lookup " + ++threadNumber);
            thread.setDaemon(true);
            return thread;
        }
    }
}
