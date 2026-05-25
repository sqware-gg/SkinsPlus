package dev.skinsplus.skin;

import java.util.Optional;

public record SkinResult(
        SkinLookupStatus status,
        String sourceName,
        Optional<SkinProperty> property,
        String message
) {
    public static SkinResult applied(String sourceName, SkinProperty property) {
        return new SkinResult(SkinLookupStatus.APPLIED, sourceName, Optional.of(property), "");
    }

    public static SkinResult disabled(String sourceName) {
        return new SkinResult(SkinLookupStatus.DISABLED, sourceName, Optional.empty(), "");
    }

    public static SkinResult missingProfile(String sourceName) {
        return new SkinResult(SkinLookupStatus.MISSING_PROFILE, sourceName, Optional.empty(), "");
    }

    public static SkinResult error(String sourceName, String message) {
        return new SkinResult(SkinLookupStatus.ERROR, sourceName, Optional.empty(), message);
    }
}
