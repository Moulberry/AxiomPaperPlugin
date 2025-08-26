package com.moulberry.axiom;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;

public class VersionHelper {

    public static void sendCustomPayload(Player player, String id, byte[] data) {
        sendCustomPayload(((CraftPlayer) player).getHandle(), id, data);
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, String id, byte[] data) {
        sendCustomPayload(serverPlayer, createResourceLocation(id), data);
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, ResourceLocation id, byte[] data) {
        serverPlayer.connection.send(new ClientboundCustomPayloadPacket(id, new FriendlyByteBuf(Unpooled.wrappedBuffer(data))));
    }

    public static void sendCustomPayloadToAll(List<ServerPlayer> players, String id, byte[] data) {
        sendCustomPayloadToAll(players, createResourceLocation(id), data);
    }

    public static void sendCustomPayloadToAll(List<ServerPlayer> players, ResourceLocation id, byte[] data) {
        if (players.isEmpty()) {
            return;
        }

        var packet = new ClientboundCustomPayloadPacket(id, new FriendlyByteBuf(Unpooled.wrappedBuffer(data)));
        for (ServerPlayer player : players) {
            player.connection.send(packet);
        }
    }

    public static ResourceLocation createResourceLocation(String composed) {
        return new ResourceLocation(composed);
    }

    public static ResourceLocation createResourceLocation(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public static ListTag getList(CompoundTag tag, String key, int type) {
        return tag.getList(key, type);
    }

}
