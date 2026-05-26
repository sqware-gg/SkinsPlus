package dev.skinsplus.skin;

import java.util.List;
import java.util.Locale;

public record SkinsPlusConfig(
        boolean autoNameLookup,
        boolean fallbackSkinsEnabled,
        String fallbackSelection,
        List<String> fallbackSkins,
        long loginLookupTimeoutSeconds,
        long skinCacheTtlHours,
        long missingProfileCacheTtlMinutes,
        boolean reapplyOnReload
) {
    public static SkinsPlusConfig defaults() {
        return new SkinsPlusConfig(
                true,
                true,
                "stable-random",
                List.of("Steve", "Alex", "Notch", "jeb_", "Dinnerbone"),
                4L,
                24L,
                15L,
                true
        );
    }

    public SkinsPlusConfig {
        fallbackSelection = normalizeFallbackSelection(fallbackSelection);
        fallbackSkins = fallbackSkins == null || fallbackSkins.isEmpty() ? defaults().fallbackSkins() : List.copyOf(fallbackSkins);
        loginLookupTimeoutSeconds = Math.max(1L, loginLookupTimeoutSeconds);
        skinCacheTtlHours = Math.max(1L, skinCacheTtlHours);
        missingProfileCacheTtlMinutes = Math.max(1L, missingProfileCacheTtlMinutes);
    }

    private static String normalizeFallbackSelection(String fallbackSelection) {
        if (fallbackSelection == null || fallbackSelection.isBlank()) {
            return "stable-random";
        }

        String normalized = fallbackSelection.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "first", "random", "stable-random" -> normalized;
            default -> "stable-random";
        };
    }
}
