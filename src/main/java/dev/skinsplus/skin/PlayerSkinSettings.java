package dev.skinsplus.skin;

import java.util.UUID;

public record PlayerSkinSettings(
        UUID playerId,
        String lastKnownName,
        SkinMode mode,
        String skinName,
        long updatedAt
) {
}
