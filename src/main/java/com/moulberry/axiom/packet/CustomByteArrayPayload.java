package com.moulberry.axiom.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CustomByteArrayPayload(ResourceLocation id, byte[] bytes) implements CustomPacketPayload {
    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(bytes);
    }
}
