package com.moulberry.axiom.world_properties;

import net.minecraft.network.FriendlyByteBuf;

public record WorldPropertyCategory(String name, boolean localizeName) {

    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeUtf(this.name);
        friendlyByteBuf.writeBoolean(this.localizeName);
    }

    public static WorldPropertyCategory read(FriendlyByteBuf friendlyByteBuf) {
        return new WorldPropertyCategory(
            friendlyByteBuf.readUtf(),
            friendlyByteBuf.readBoolean()
        );
    }

}
