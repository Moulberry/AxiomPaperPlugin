package com.moulberry.axiom;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class VersionHelper {

    public static void sendCustomPayload(Player player, String id, byte[] data) {
        sendCustomPayload(((CraftPlayer) player).getHandle(), id, data);
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, String id, byte[] data) {
        sendCustomPayload(serverPlayer, createResourceLocation(id), data);
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, ResourceLocation id, byte[] data) {
        serverPlayer.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, Unpooled.wrappedBuffer(data))));
    }

    public static ResourceLocation createResourceLocation(String composed) {
        return ResourceLocation.parse(composed);
    }

    public static ResourceLocation createResourceLocation(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

}
