package com.moulberry.axiom.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AxiomManipulateEntityEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID entityUUID;
    private final Location fromLocation;
    private final Location toLocation;

    private boolean cancelled = false;

    public AxiomManipulateEntityEvent(Player player, Entity entity, Location toLocation) {
        this.player = player;
        this.entityUUID = entity.getUniqueId();
        this.fromLocation = entity.getLocation();
        this.toLocation = toLocation;
    }

    public AxiomManipulateEntityEvent(Player player, UUID entityUUID, Location fromLocation, Location toLocation) {
        this.player = player;
        this.entityUUID = entityUUID;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getFromLocation() {
        return fromLocation;
    }

    public Location getToLocation() {
        return toLocation;
    }

    /**
     * Gets a BukkitEntity from the given UUID.
     * Do note this might return null in cases where the UUID was from a client-side entity
     * @return The BukkitEntity, if it exists
     */
    @Nullable
    public Entity getEntity() {
        return Bukkit.getEntity(entityUUID);
    }

    public UUID getUUID() {
        return entityUUID;
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
