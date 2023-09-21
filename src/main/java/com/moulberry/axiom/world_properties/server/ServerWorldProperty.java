package com.moulberry.axiom.world_properties.server;

import com.moulberry.axiom.world_properties.WorldPropertyDataType;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    public void update(ServerLevel serverLevel, byte[] data) {
        this.value = this.widget.dataType().deserialize(data);
        if (this.handler.test(this.value)) {
//            AxiomClientboundSetWorldProperty packet = new AxiomClientboundSetWorldProperty(this.id,
//                this.widget.dataType().getTypeId(), this.widget.dataType().serialize(this.value));

//            for (ServerPlayer player : serverLevel.players()) {
//                if (player.hasPermissions(2)) packet.send(player);
//            }
        }
    }

    public void setValue(T value) {
        this.value = value;
    }

    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeResourceLocation(this.id);
        friendlyByteBuf.writeUtf(this.name);
        friendlyByteBuf.writeBoolean(this.localizeName);
        this.widget.write(friendlyByteBuf);
        friendlyByteBuf.writeByteArray(this.widget.dataType().serialize(this.value));
    }

}
