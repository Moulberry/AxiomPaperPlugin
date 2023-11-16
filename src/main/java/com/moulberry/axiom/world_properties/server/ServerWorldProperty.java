package com.moulberry.axiom.world_properties.server;

import com.moulberry.axiom.world_properties.PropertyUpdateHandler;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.function.Function;

public class ServerWorldProperty<T> extends ServerWorldPropertyBase<T> {

    private final Function<World, T> defaultValueFunction;
    private final PropertyUpdateHandler<T> handler;

    public ServerWorldProperty(NamespacedKey id, String name, boolean localizeName, WorldPropertyWidgetType<T> widget,
                               Function<World, T> defaultValueFunction, PropertyUpdateHandler<T> handler) {
        super(id, name, localizeName, widget);
        this.defaultValueFunction = defaultValueFunction;
        this.handler = handler;
    }

    @Override
    public T getDefaultValue(World world) {
        return this.defaultValueFunction.apply(world);
    }

    @Override
    public PropertyUpdateResult handleUpdateProperty(Player player, World world, T value) {
        return this.handler.update(player, world, value);
    }

}
