package com.moulberry.axiom;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class VersionHelper {

    @FunctionalInterface
    private interface DiscardedPayloadConstructor {
        DiscardedPayload create(ResourceLocation id, byte[] data) throws IllegalAccessException, InstantiationException, InvocationTargetException;
    }
    private static DiscardedPayloadConstructor discardedPayloadConstructor = null;

    public static DiscardedPayload createCustomPayload(ResourceLocation id, byte[] data) {
        if (discardedPayloadConstructor == null) {
            findDiscardedPayloadConstructor();
        }
        try {
            return discardedPayloadConstructor.create(id, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void findDiscardedPayloadConstructor() {
        for (Constructor<?> ctor : DiscardedPayload.class.getConstructors()) {
            var parameters = ctor.getParameters();
            if (parameters.length == 2) {
                var parameter1 = parameters[0].getType();
                if (ResourceLocation.class.isAssignableFrom(parameter1)) {
                    var parameter2 = parameters[1].getType();
                    if (byte[].class.isAssignableFrom(parameter2)) {
                        discardedPayloadConstructor = (id1, data1) -> (DiscardedPayload) ctor.newInstance(id1, data1);
                        break;
                    } else if (io.netty.buffer.ByteBuf.class.isAssignableFrom(parameter2)) {
                        discardedPayloadConstructor = (id1, data1) -> (DiscardedPayload) ctor.newInstance(id1, Unpooled.wrappedBuffer(data1));
                        break;
                    }
                }
            }
        }
        if (discardedPayloadConstructor == null) {
            throw new RuntimeException("Unable to find suitable DiscardedPayload constructor");
        }
    }

    public static void sendCustomPayload(Player player, String id, byte[] data) {
        sendCustomPayload(((CraftPlayer) player).getHandle(), id, data);
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, String id, byte[] data) {
        sendCustomPayload(serverPlayer, createResourceLocation(id), data);
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, ResourceLocation id, byte[] data) {
        var payload = createCustomPayload(id, data);
        serverPlayer.connection.send(new ClientboundCustomPayloadPacket(payload));
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

}
