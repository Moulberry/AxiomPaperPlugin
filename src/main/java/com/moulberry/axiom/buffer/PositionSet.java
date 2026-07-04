package com.moulberry.axiom.buffer;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public class PositionSet {

    private final Long2ObjectMap<short[]> map;
    private int count = 0;

    public PositionSet() {
        this.map  = new Long2ObjectOpenHashMap<>();
        this.count = 0;
    }

    private PositionSet(Long2ObjectMap<short[]> map, int count) {
        this.map = map;
        this.count = count;
    }

    public int chunkCount() {
        return this.map.size();
    }

    public int count() {
        return this.count;
    }

    public boolean isEmpty() {
        return this.count == 0;
    }

    public void forEach(TriIntConsumer consumer) {
        for (Long2ObjectMap.Entry<short[]> entry : this.map.long2ObjectEntrySet()) {
            int cx = BlockPos.getX(entry.getLongKey()) * 16;
            int cy = BlockPos.getY(entry.getLongKey()) * 16;
            int cz = BlockPos.getZ(entry.getLongKey()) * 16;

            int index = 0;
            for (int z=0; z<16; z++) {
                for (int y=0; y<16; y++) {
                    short v = entry.getValue()[index++];

                    // manually unrolled loop gives 1.7x perf improvement
                    if (v == -1) {
                        consumer.accept(cx+0, cy+y, cz+z);
                        consumer.accept(cx+1, cy+y, cz+z);
                        consumer.accept(cx+2, cy+y, cz+z);
                        consumer.accept(cx+3, cy+y, cz+z);
                        consumer.accept(cx+4, cy+y, cz+z);
                        consumer.accept(cx+5, cy+y, cz+z);
                        consumer.accept(cx+6, cy+y, cz+z);
                        consumer.accept(cx+7, cy+y, cz+z);
                        consumer.accept(cx+8, cy+y, cz+z);
                        consumer.accept(cx+9, cy+y, cz+z);
                        consumer.accept(cx+10, cy+y, cz+z);
                        consumer.accept(cx+11, cy+y, cz+z);
                        consumer.accept(cx+12, cy+y, cz+z);
                        consumer.accept(cx+13, cy+y, cz+z);
                        consumer.accept(cx+14, cy+y, cz+z);
                        consumer.accept(cx+15, cy+y, cz+z);
                    } else if (v != 0) {
                        if ((v & (1 << 0)) != 0) consumer.accept(cx+0, cy+y, cz+z);
                        if ((v & (1 << 1)) != 0) consumer.accept(cx+1, cy+y, cz+z);
                        if ((v & (1 << 2)) != 0) consumer.accept(cx+2, cy+y, cz+z);
                        if ((v & (1 << 3)) != 0) consumer.accept(cx+3, cy+y, cz+z);
                        if ((v & (1 << 4)) != 0) consumer.accept(cx+4, cy+y, cz+z);
                        if ((v & (1 << 5)) != 0) consumer.accept(cx+5, cy+y, cz+z);
                        if ((v & (1 << 6)) != 0) consumer.accept(cx+6, cy+y, cz+z);
                        if ((v & (1 << 7)) != 0) consumer.accept(cx+7, cy+y, cz+z);
                        if ((v & (1 << 8)) != 0) consumer.accept(cx+8, cy+y, cz+z);
                        if ((v & (1 << 9)) != 0) consumer.accept(cx+9, cy+y, cz+z);
                        if ((v & (1 << 10)) != 0) consumer.accept(cx+10, cy+y, cz+z);
                        if ((v & (1 << 11)) != 0) consumer.accept(cx+11, cy+y, cz+z);
                        if ((v & (1 << 12)) != 0) consumer.accept(cx+12, cy+y, cz+z);
                        if ((v & (1 << 13)) != 0) consumer.accept(cx+13, cy+y, cz+z);
                        if ((v & (1 << 14)) != 0) consumer.accept(cx+14, cy+y, cz+z);
                        if ((v & (1 << 15)) != 0) consumer.accept(cx+15, cy+y, cz+z);
                    }
                }
            }
        }
    }

    public static PositionSet read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Long2ObjectMap<short[]> map = new Long2ObjectOpenHashMap<>(Math.min(256, size));

        int count = 0;
        for (int i = 0; i < size; i++) {
            long pos = buf.readLong();

            short[] array = new short[16*16];
            for (int j = 0; j < 16*16; j++) {
                short s = buf.readShort();
                count += Integer.bitCount(s & 0xFFFF);
                array[j] = s;
            }

            map.put(pos, array);
        }

        return new PositionSet(map, count);
    }

}
