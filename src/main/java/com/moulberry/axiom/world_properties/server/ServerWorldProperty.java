package com.moulberry.axiom.world_properties.server;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.world_properties.WorldPropertyDataType;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

public class ServerWorldProperty<T> {

    private final ResourceLocation id;
    private final String name;
    private final boolean localizeName;
    private WorldPropertyWidgetType<T> widget;
    private T value;
    private Predicate<T> handler;

    public ServerWorldProperty(ResourceLocation id, String name, boolean localizeName, WorldPropertyWidgetType<T> widget,
            T value, Predicate<T> handler) {
        this.id = id;
        this.name = name;
        this.localizeName = localizeName;
        this.widget = widget;
        this.value = value;
        this.handler = handler;
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public WorldPropertyDataType<T> getType() {
        return this.widget.dataType();
    }

    public void update(World world, byte[] data) {
        this.value = this.widget.dataType().deserialize(data);
        if (this.handler.test(this.value)) {
            this.sync(world);
        }
    }

    public void setValueWithoutSyncing(T value) {
        this.value = value;
    }

    public void setValue(World world, T value) {
        this.value = value;
        this.sync(world);
    }

    public void sync(World world) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeResourceLocation(this.id);
        buf.writeVarInt(this.widget.dataType().getTypeId());
        buf.writeByteArray(this.widget.dataType().serialize(this.value));

        byte[] message = buf.accessByteBufWithCorrectSize();
        for (Player player : world.getPlayers()) {
            if (AxiomPaper.PLUGIN.activeAxiomPlayers.contains(player.getUniqueId())) {
                player.sendPluginMessage(AxiomPaper.PLUGIN, "axiom:set_world_property", message);
            }
        }
    }

    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeResourceLocation(this.id);
        friendlyByteBuf.writeUtf(this.name);
        friendlyByteBuf.writeBoolean(this.localizeName);
        this.widget.write(friendlyByteBuf);
        friendlyByteBuf.writeByteArray(this.widget.dataType().serialize(this.value));
    }

}
