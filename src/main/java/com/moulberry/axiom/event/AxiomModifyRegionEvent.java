package com.moulberry.axiom.event;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AxiomModifyRegionEvent extends Event implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean isCancelled;

    private final Player player;
    private final World world;
    private final long minX;
    private final long minY;
    private final long minZ;
    private final long maxX;
    private final long maxY;
    private final long maxZ;

    public AxiomModifyRegionEvent(Player player, World world, long minX, long minY, long minZ, long maxX, long maxY, long maxZ) {
        this.player = player;
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return this.isCancelled;
    }

    @Override
    public void setCancelled(boolean isCancelled) {
        this.isCancelled = isCancelled;
    }

    public Player getPlayer() {
        return player;
    }

    public World getWorld() {
        return world;
    }

    public long getMinX() {
        return minX;
    }

    public long getMinY() {
        return minY;
    }

    public long getMinZ() {
        return minZ;
    }

    public long getMaxX() {
        return maxX;
    }

    public long getMaxY() {
        return maxY;
    }

    public long getMaxZ() {
        return maxZ;
    }
}
