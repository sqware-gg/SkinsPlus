package dev.skinsplus.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public final class SkinApplier {
    private static final String TEXTURES_PROPERTY = "textures";

    private final Plugin plugin;

    public SkinApplier(Plugin plugin) {
        this.plugin = plugin;
    }

    public PlayerProfile withSkinProperty(PlayerProfile original, SkinProperty skinProperty) {
        PlayerProfile profile = copyProfile(original);
        profile.removeProperty(TEXTURES_PROPERTY);
        if (skinProperty.signed()) {
            profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, skinProperty.value(), skinProperty.signature()));
        } else {
            profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, skinProperty.value()));
        }
        return profile;
    }

    public void apply(Player player, Optional<SkinProperty> property) {
        PlayerProfile profile = copyProfile(player.getPlayerProfile());
        profile.removeProperty(TEXTURES_PROPERTY);
        property.ifPresent(skinProperty -> {
            if (skinProperty.signed()) {
                profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, skinProperty.value(), skinProperty.signature()));
            } else {
                profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, skinProperty.value()));
            }
        });
        player.setPlayerProfile(profile);
        refreshForViewers(player);
    }

    private PlayerProfile copyProfile(PlayerProfile original) {
        PlayerProfile profile = Bukkit.createProfile(original.getId(), original.getName());
        profile.setProperties(original.getProperties());
        return profile;
    }

    private void refreshForViewers(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player) || !viewer.canSee(player)) {
                continue;
            }
            viewer.hidePlayer(plugin, player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline() && player.isOnline()) {
                    viewer.showPlayer(plugin, player);
                }
            }, 2L);
        }
    }
}
