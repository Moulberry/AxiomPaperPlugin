package com.moulberry.axiom.world_properties.server;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.world_properties.WorldPropertyDataType;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R2.util.CraftNamespacedKey;
import org.bukkit.entity.Player;

public abstract class ServerWorldPropertyBase<T> {

    private final ResourceLocation id;
    /*package-private*/ final String name;
    /*package-private*/ final boolean localizeName;
    /*package-private*/ WorldPropertyWidgetType<T> widget;

    public ServerWorldPropertyBase(NamespacedKey id, String name, boolean localizeName, WorldPropertyWidgetType<T> widget) {
        this.id = CraftNamespacedKey.toMinecraft(id);
        this.name = name;
        this.localizeName = localizeName;
        this.widget = widget;
    }

    public abstract T getDefaultValue(World world);

    public abstract PropertyUpdateResult handleUpdateProperty(Player player, World world, T value);

    public ResourceLocation getId() {
        return this.id;
    }

    public WorldPropertyDataType<T> getType() {
        return this.widget.dataType();
    }

    @SuppressWarnings("unchecked")
    public boolean setValueWithoutSyncing(World world, T value) {
        ServerWorldPropertiesRegistry properties = AxiomPaper.PLUGIN.getWorldPropertiesIfPresent(world);
        if (properties != null) {
            ServerWorldPropertyHolder<?> property = properties.getById(this.id);
            if (property != null && property.getProperty() == this) {
                ((ServerWorldPropertyHolder<T>)property).setValueWithoutSyncing(value);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public boolean setValue(World world, T value) {
        ServerWorldPropertiesRegistry properties = AxiomPaper.PLUGIN.getWorldPropertiesIfPresent(world);
        if (properties != null) {
            ServerWorldPropertyHolder<?> property = properties.getById(this.id);
            if (property != null && property.getProperty() == this) {
                ((ServerWorldPropertyHolder<T>)property).setValue(world, value);
                return true;
            }
        }
        return false;
    }

}
