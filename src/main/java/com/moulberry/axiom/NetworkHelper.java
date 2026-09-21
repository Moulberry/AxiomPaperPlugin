package com.moulberry.axiom;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class NetworkHelper {

    public static BlockHitResult readBlockHitResult(ByteBuf input) {
        BlockPos pos = BlockPos.of(input.readLong());
        byte directionId = input.readByte();
        Direction face = Direction.from3DDataValue(directionId);
        float clickX = input.readFloat();
        float clickY = input.readFloat();
        float clickZ = input.readFloat();
        boolean inside = input.readBoolean();
        boolean worldBorder = input.readBoolean();
        return new BlockHitResult(new Vec3((double)pos.getX() + clickX, (double)pos.getY() + clickY, (double)pos.getZ() + (double)clickZ), face, pos, inside, worldBorder);
    }

    public static <T> void writeCollection(FriendlyByteBuf friendlyByteBuf, Collection<T> collection, StreamEncoder<FriendlyByteBuf, T> biConsumer) {
        friendlyByteBuf.writeVarInt(collection.size());
        for (T item : collection) {
            biConsumer.encode(friendlyByteBuf, item);
        }
    }

    public static <K, V> void writeMap(FriendlyByteBuf friendlyByteBuf, Map<K, V> map, StreamEncoder<FriendlyByteBuf, K> keyWriter, StreamEncoder<FriendlyByteBuf, V> valueWriter) {
        friendlyByteBuf.writeVarInt(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            keyWriter.encode(friendlyByteBuf, entry.getKey());
            valueWriter.encode(friendlyByteBuf, entry.getValue());
        }
    }

    public static <T> ArrayList<T> readList(FriendlyByteBuf friendlyByteBuf, StreamDecoder<FriendlyByteBuf, T> function, int limit) {
        int count = friendlyByteBuf.readVarInt();

        if (count > limit) {
            throw new DecoderException("Collection size " + count + " is too large; max " + limit);
        }

        ArrayList<T> list = new ArrayList<>(Math.min(1024, count));

        for (int i = 0; i < count; i++) {
            T item = function.decode(friendlyByteBuf);
            list.add(item);
        }

        return list;
    }

    public static <K, V> LinkedHashMap<K, V> readLinkedHashMap(FriendlyByteBuf friendlyByteBuf, StreamDecoder<FriendlyByteBuf, K> keys, StreamDecoder<FriendlyByteBuf, V> values, int limit) {
        int count = friendlyByteBuf.readVarInt();

        if (count > limit) {
            throw new DecoderException("Collection size " + count + " is too large; max " + limit);
        }

        LinkedHashMap<K, V> map = new LinkedHashMap<>(Math.min(1024, count));

        for (int i = 0; i < count; i++) {
            K key = keys.decode(friendlyByteBuf);
            V value = values.decode(friendlyByteBuf);
            map.put(key, value);
        }

        return map;
    }

    public static <T> LinkedHashSet<T> readLinkedHashSet(FriendlyByteBuf friendlyByteBuf, StreamDecoder<FriendlyByteBuf, T> function, int limit) {
        int count = friendlyByteBuf.readVarInt();

        if (count > limit) {
            throw new DecoderException("Collection size " + count + " is too large; max " + limit);
        }

        LinkedHashSet<T> list = new LinkedHashSet<>(Math.min(1024, count));

        for (int i = 0; i < count; i++) {
            T item = function.decode(friendlyByteBuf);
            list.add(item);
        }

        return list;
    }

}
