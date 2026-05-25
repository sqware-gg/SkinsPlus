package dev.skinsplus.skin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SkinsPlusConfigLoader {
    private SkinsPlusConfigLoader() {
    }

    public static SkinsPlusConfig load(Path configFile, SkinLogger logger) {
        SkinsPlusConfig defaults = SkinsPlusConfig.defaults();
        Map<String, Object> root = YamlData.load(configFile, logger);
        Map<String, Object> fallback = YamlData.section(root, "fallback-skins");
        return new SkinsPlusConfig(
                YamlData.bool(root, "auto-name-lookup", defaults.autoNameLookup()),
                YamlData.bool(fallback, "enabled", defaults.fallbackSkinsEnabled()),
                YamlData.string(fallback, "selection", defaults.fallbackSelection()),
                fallbackList(fallback, defaults.fallbackSkins()),
                YamlData.longValue(root, "login-lookup-timeout-seconds", defaults.loginLookupTimeoutSeconds()),
                YamlData.longValue(root, "skin-cache-ttl-hours", defaults.skinCacheTtlHours()),
                YamlData.longValue(root, "missing-profile-cache-ttl-minutes", defaults.missingProfileCacheTtlMinutes()),
                YamlData.bool(root, "reapply-on-reload", defaults.reapplyOnReload())
        );
    }

    private static List<String> fallbackList(Map<String, Object> fallback, List<String> defaults) {
        Object value = fallback.get("list");
        if (!(value instanceof List<?> rawList)) {
            return defaults;
        }

        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString());
            }
        }
        return result.isEmpty() ? defaults : result;
    }

}
