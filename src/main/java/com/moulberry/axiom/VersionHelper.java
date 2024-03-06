package com.moulberry.axiom;

import com.moulberry.axiom.packet.CustomByteArrayPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class VersionHelper {

    public static void sendCustomPayload(ServerPlayer serverPlayer, ResourceLocation id, byte[] data) {
        serverPlayer.connection.send(new ClientboundCustomPayloadPacket(new CustomByteArrayPayload(id, data)));
    }

}
