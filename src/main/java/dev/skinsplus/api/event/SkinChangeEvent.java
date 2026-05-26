package dev.skinsplus.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class SkinChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID playerUuid;
    private final String playerName;
    private final String mode;
    private final String sourceName;
    private final boolean applied;
    private final boolean rolledBack;

    public SkinChangeEvent(Player player, String mode, String sourceName, boolean applied, boolean rolledBack) {
        this.player = player;
        this.playerUuid = player.getUniqueId();
        this.playerName = player.getName();
        this.mode = mode == null ? "" : mode;
        this.sourceName = sourceName == null ? "" : sourceName;
        this.applied = applied;
        this.rolledBack = rolledBack;
    }

    public Player player() {
        return player;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String playerName() {
        return playerName;
    }

    public String mode() {
        return mode;
    }

    public String sourceName() {
        return sourceName;
    }

    public boolean applied() {
        return applied;
    }

    public boolean rolledBack() {
        return rolledBack;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
