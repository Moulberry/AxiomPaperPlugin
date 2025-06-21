package com.moulberry.axiom;

import com.moulberry.axiom.packet.CustomByteArrayPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class VersionHelper {

    public static void sendCustomPayload(Player player, String id, byte[] data) {
        sendCustomPayload(((CraftPlayer) player).getHandle(), id, data);
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, String id, byte[] data) {
        sendCustomPayload(serverPlayer, createResourceLocation(id), data);
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, ResourceLocation id, byte[] data) {
        serverPlayer.connection.send(new ClientboundCustomPayloadPacket(new CustomByteArrayPayload(id, data)));
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, ResourceLocation id, FriendlyByteBuf friendlyByteBuf) {
        byte[] data = new byte[friendlyByteBuf.writerIndex()];
        friendlyByteBuf.getBytes(friendlyByteBuf.readerIndex(), data);
        sendCustomPayload(serverPlayer, id, data);
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
