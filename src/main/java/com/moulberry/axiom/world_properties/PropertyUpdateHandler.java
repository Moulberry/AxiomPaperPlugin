package com.moulberry.axiom.world_properties;

import org.bukkit.World;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface PropertyUpdateHandler<T> {

    /**
     * @param player the player that updated the property
     * @param world the world for which the property has been updated
     * @param value the new value of the property
     * @return whether to sync the value back to the client
     */
    boolean update(Player player, World world, T value);

}
