package com.moulberry.axiom.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AxiomTimeChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final @Nullable Integer time;
    private final @Nullable Boolean freezeTime;
    private boolean cancelled = false;

    public AxiomTimeChangeEvent(Player player, @Nullable Integer time, @Nullable Boolean freezeTime) {
        this.player = player;
        this.time = time;
        this.freezeTime = freezeTime;
    }

    public @Nullable Integer getTime() {
        return time;
    }

    public @Nullable Boolean isFreezeTime() {
        return freezeTime;
    }

    public Player getPlayer() {
        return this.player;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

}
