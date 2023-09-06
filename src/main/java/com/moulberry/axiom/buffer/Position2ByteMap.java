package com.moulberry.axiom.buffer;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.AxiomPaper;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;
import java.util.function.LongFunction;

public class Position2ByteMap {

    @FunctionalInterface
    public interface EntryConsumer {
        void consume(int x, int y, int z, byte v);
    }

    private final byte defaultValue;
    private final LongFunction<byte[]> defaultFunction;
    private final Long2ObjectMap<byte[]> map = new Long2ObjectOpenHashMap<>();

    private long lastChunkPos = AxiomConstants.MIN_POSITION_LONG;
    private byte[] lastChunk = null;

    public Position2ByteMap() {
        this((byte) 0);
    }

    public Position2ByteMap(byte defaultValue) {
        this.defaultValue = defaultValue;

        if (defaultValue == 0) {
            this.defaultFunction = k -> new byte[16*16*16];
        } else {
            this.defaultFunction = k -> {
                byte[] array = new byte[16*16*16];
                Arrays.fill(array, defaultValue);
                return array;
            };
        }
    }

    public void save(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeByte(this.defaultValue);
        for (Long2ObjectMap.Entry<byte[]> entry : this.map.long2ObjectEntrySet()) {
            friendlyByteBuf.writeLong(entry.getLongKey());
            friendlyByteBuf.writeBytes(entry.getValue());
        }
        friendlyByteBuf.writeLong(AxiomConstants.MIN_POSITION_LONG);
    }

    public static Position2ByteMap load(FriendlyByteBuf friendlyByteBuf) {
        Position2ByteMap map = new Position2ByteMap(friendlyByteBuf.readByte());

        while (true) {
            long pos = friendlyByteBuf.readLong();
            if (pos == AxiomConstants.MIN_POSITION_LONG) break;

            byte[] bytes = new byte[16*16*16];
            friendlyByteBuf.readBytes(bytes);
            map.map.put(pos, bytes);
        }

        return map;
    }

    public void clear() {
        this.map.clear();
        this.lastChunkPos = AxiomConstants.MIN_POSITION_LONG;
        this.lastChunk = null;
    }

    public byte get(int x, int y, int z) {
        int xC = x >> 4;
        int yC = y >> 4;
        int zC = z >> 4;

        byte[] array = this.getChunk(xC, yC, zC);
        if (array == null) return this.defaultValue;

        return array[(x&15) + (y&15)*16 + (z&15)*16*16];
    }

    public void put(int x, int y, int z, byte v) {
        int xC = x >> 4;
        int yC = y >> 4;
        int zC = z >> 4;

        byte[] array = this.getOrCreateChunk(xC, yC, zC);
        array[(x&15) + (y&15)*16 + (z&15)*16*16] = v;
    }

    public byte add(int x, int y, int z, byte v) {
        if (v == 0) return this.get(x, y, z);

        int xC = x >> 4;
        int yC = y >> 4;
        int zC = z >> 4;

        byte[] array = this.getOrCreateChunk(xC, yC, zC);
        return array[(x&15) + (y&15)*16 + (z&15)*16*16] += v;
    }


    public byte binaryAnd(int x, int y, int z, byte v) {
        int xC = x >> 4;
        int yC = y >> 4;
        int zC = z >> 4;

        byte[] array = this.getOrCreateChunk(xC, yC, zC);
        return array[(x&15) + (y&15)*16 + (z&15)*16*16] &= v;
    }

    public boolean min(int x, int y, int z, byte v) {
        int xC = x >> 4;
        int yC = y >> 4;
        int zC = z >> 4;

        byte[] array = this.getOrCreateChunk(xC, yC, zC);
        int index = (x&15) + (y&15)*16 + (z&15)*16*16;

        if (v < array[index]) {
            array[index] = v;
            return true;
        } else {
            return false;
        }
    }

    public void forEachEntry(EntryConsumer consumer) {
        for (Long2ObjectMap.Entry<byte[]> entry : this.map.long2ObjectEntrySet()) {
            int cx = BlockPos.getX(entry.getLongKey()) * 16;
            int cy = BlockPos.getY(entry.getLongKey()) * 16;
            int cz = BlockPos.getZ(entry.getLongKey()) * 16;

            int index = 0;
            for (int z=0; z<16; z++) {
                for (int y=0; y<16; y++) {
                    for (int x=0; x<16; x++) {
                        byte v = entry.getValue()[index++];
                        if (v != this.defaultValue) {
                            consumer.consume(cx + x, cy + y, cz + z, v);
                        }
                    }
                }
            }
        }
    }

    public byte[] getChunk(int xC, int yC, int zC) {
        return this.getChunk(BlockPos.asLong(xC, yC, zC));
    }

    public byte[] getChunk(long pos) {
        if (this.lastChunkPos != pos) {
            byte[] chunk = this.map.get(pos);
            this.lastChunkPos = pos;
            this.lastChunk = chunk;
        }

        return this.lastChunk;
    }

    public byte[] getOrCreateChunk(int xC, int yC, int zC) {
        return this.getOrCreateChunk(BlockPos.asLong(xC, yC, zC));
    }

    public byte[] getOrCreateChunk(long pos) {
        if (this.lastChunk == null || this.lastChunkPos != pos) {
            byte[] chunk = this.map.computeIfAbsent(pos, this.defaultFunction);
            this.lastChunkPos = pos;
            this.lastChunk = chunk;
        }

        return this.lastChunk;
    }

}
