package com.moulberry.axiom;

import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.SharedConstants;
import net.minecraft.core.IdMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.phys.BlockHitResult;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public class VersionHelper {

    @FunctionalInterface
    private interface DiscardedPayloadConstructor {
        DiscardedPayload create(Identifier id, byte[] data) throws IllegalAccessException, InstantiationException, InvocationTargetException;
    }
    private static DiscardedPayloadConstructor discardedPayloadConstructor = null;

    public static DiscardedPayload createCustomPayload(Identifier id, byte[] data) {
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
                if (Identifier.class.isAssignableFrom(parameter1)) {
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
        sendCustomPayload(serverPlayer, createIdentifier(id), data);
    }

    public static void sendCustomPayload(ServerPlayer serverPlayer, Identifier id, byte[] data) {
        var payload = createCustomPayload(id, data);
        serverPlayer.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    public static void sendCustomPayloadToAll(List<ServerPlayer> players, String id, byte[] data) {
        sendCustomPayloadToAll(players, createIdentifier(id), data);
    }

    public static void sendCustomPayloadToAll(List<ServerPlayer> players, Identifier id, byte[] data) {
        if (players.isEmpty()) {
            return;
        }

        var payload = createCustomPayload(id, data);
        var packet = new ClientboundCustomPayloadPacket(payload);
        for (ServerPlayer player : players) {
            player.connection.send(packet);
        }
    }

    public static Identifier createIdentifier(String composed) {
        return Identifier.parse(composed);
    }

    public static Identifier createIdentifier(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static ListTag getList(CompoundTag tag, String key, int type) {
        ListTag list = tag.getListOrEmpty(key);
        if (list.isEmpty()) {
            return list;
        }
        if (list.get(0).getId() != type) {
            return new ListTag();
        } else {
            return list;
        }
    }

    public static PalettedContainer<BlockState> createPalettedContainer(IdMap<BlockState> map, BlockState defaultValue) {
        return new PalettedContainer<>(defaultValue, Strategy.createForBlockStates(map));
    }

    public static Codec<PalettedContainer<BlockState>> createPalettedContainerCodec(Codec<BlockState> codec, IdMap<BlockState> map, BlockState defaultValue) {
        return PalettedContainer.codecRW(codec, Strategy.createForBlockStates(map), defaultValue);
    }

    //StreamCodec migration because of the missings methods in FriendlyByteBuf.

    public static <T> IntFunction<T> limitValue(IntFunction<T> app, int limit) {
        return size -> {
            if (size > limit)
            {
                throw new DecoderException("Value " + size + " is larger than limit " + limit);
            }
            return app.apply(size);
        };
    }

    public static <T> void writeCollection(FriendlyByteBuf buf, Collection<T> coll, StreamEncoder<? super FriendlyByteBuf, T> encoder) {
        VarInt.write(buf, coll.size());
        for (T element : coll)
        {
            encoder.encode(buf, element);
        }
    }

    public static <T, C extends Collection<T>> C readCollection(FriendlyByteBuf buf, IntFunction<C> factory, StreamDecoder<? super FriendlyByteBuf, T> decoder) {
        int size = VarInt.read(buf);
        C collection = factory.apply(size);
        for (int i = 0; i < size; i++)
        {
            collection.add(decoder.decode(buf));
        }
        return collection;
    }

    public static <T> List<T> readList(FriendlyByteBuf buf, StreamDecoder<? super FriendlyByteBuf, T> decoder) {
        return readCollection(buf, ArrayList::new, decoder);
    }

    public static <K, V> void writeMap(FriendlyByteBuf buf, Map<K, V> map, StreamEncoder<? super FriendlyByteBuf, K> keyEncoder, StreamEncoder<? super FriendlyByteBuf, V> valueEncoder) {
        VarInt.write(buf, map.size());
        map.forEach((key, value) -> {
            keyEncoder.encode(buf, key);
            valueEncoder.encode(buf, value);
        });
    }

    public static <K, V, M extends Map<K, V>> M readMap(FriendlyByteBuf buf, IntFunction<M> factory, StreamDecoder<? super FriendlyByteBuf, K> keyDecoder, StreamDecoder<? super FriendlyByteBuf, V> valueDecoder) {
        int size = VarInt.read(buf);
        M map = factory.apply(size);
        for (int i = 0; i < size; i++)
        {
            K key = keyDecoder.decode(buf);
            V value = valueDecoder.decode(buf);
            map.put(key, value);
        }
        return map;
    }

    public static BlockHitResult readBlockHitResult(FriendlyByteBuf buf)
    {
        return BlockHitResult.STREAM_CODEC.decode(buf);
    }

    public static String getVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

}
