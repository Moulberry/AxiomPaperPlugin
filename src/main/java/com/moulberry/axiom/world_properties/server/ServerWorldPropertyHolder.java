package com.moulberry.axiom.world_properties.server;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.world_properties.PropertyUpdateHandler;
import com.moulberry.axiom.world_properties.WorldPropertyDataType;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R2.util.CraftNamespacedKey;
import org.bukkit.entity.Player;

import java.util.Objects;

public class ServerWorldPropertyHolder<T> {

    private T value;
    private ServerWorldPropertyBase<T> property;
    private boolean unsyncedValue = false;

    public ServerWorldPropertyHolder(T value, ServerWorldPropertyBase<T> property) {
        this.value = value;
        this.property = property;
    }

    public ResourceLocation getId() {
        return this.property.getId();
    }

    public WorldPropertyDataType<T> getType() {
        return this.property.widget.dataType();
    }

    public ServerWorldPropertyBase<T> getProperty() {
        return property;
    }

    public void update(Player player, World world, byte[] data) {
        T newValue = this.property.widget.dataType().deserialize(data);

        PropertyUpdateResult result = this.property.handleUpdateProperty(player, world, newValue);

        if (result.isUpdate()) {
            this.value = newValue;

            if (result.isSync()) {
                this.sync(world);
            } else {
                this.unsyncedValue = true;
            }
        }
    }

    public void setValueWithoutSyncing(T value) {
        this.value = value;
    }

    public void setValue(World world, T value) {
        boolean sync = this.unsyncedValue || !Objects.equals(value, this.value);
        this.value = value;
        if (sync) {
            this.sync(world);
        }
    }

    public void sync(World world) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeResourceLocation(this.getId());
        buf.writeVarInt(this.property.widget.dataType().getTypeId());
        buf.writeByteArray(this.property.widget.dataType().serialize(this.value));

        byte[] message = new byte[buf.writerIndex()];
        buf.getBytes(0, message);
        for (Player player : world.getPlayers()) {
            if (AxiomPaper.PLUGIN.activeAxiomPlayers.contains(player.getUniqueId())) {
                player.sendPluginMessage(AxiomPaper.PLUGIN, "axiom:set_world_property", message);
            }
        }

        this.unsyncedValue = false;
    }

    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeResourceLocation(this.getId());
        friendlyByteBuf.writeUtf(this.property.name);
        friendlyByteBuf.writeBoolean(this.property.localizeName);
        this.property.widget.write(friendlyByteBuf);
        friendlyByteBuf.writeByteArray(this.property.widget.dataType().serialize(this.value));
    }

}
