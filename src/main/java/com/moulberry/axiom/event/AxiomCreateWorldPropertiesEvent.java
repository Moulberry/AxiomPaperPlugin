package com.moulberry.axiom.event;

import com.moulberry.axiom.world_properties.WorldPropertyCategory;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import com.moulberry.axiom.world_properties.server.ServerWorldProperty;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertyBase;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertyHolder;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AxiomCreateWorldPropertiesEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final ServerWorldPropertiesRegistry registry;
    private boolean cancelled = false;

    public AxiomCreateWorldPropertiesEvent(World world, ServerWorldPropertiesRegistry registry) {
        this.world = world;
        this.registry = registry;
    }

    public World getWorld() {
        return world;
    }

    public void addCategory(WorldPropertyCategory category, List<ServerWorldPropertyBase<?>> properties) {
        this.registry.addCategory(category, properties);
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
