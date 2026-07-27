package com.moulberry.axiom.world_properties;

import com.moulberry.axiom.world_properties.server.PropertyUpdateResult;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface PropertyUpdateHandler<T> {

    /**
     * @param player the player that updated the property, or null for a server-initiated restore
     * @param world the world for which the property has been updated
     * @param value the new value of the property
     * @return {@link PropertyUpdateResult}
     */
    PropertyUpdateResult update(@Nullable Player player, World world, T value);

}
