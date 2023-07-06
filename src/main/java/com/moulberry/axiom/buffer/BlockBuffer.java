package com.moulberry.axiom.buffer;

import com.moulberry.axiom.AxiomPaper;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

public class BlockBuffer {

    public static final BlockState EMPTY_STATE = Blocks.STRUCTURE_VOID.defaultBlockState();

    private final Long2ObjectMap<PalettedContainer<BlockState>> values;

    private PalettedContainer<BlockState> last = null;
    private long lastId = AxiomPaper.MIN_POSITION_LONG;
    private int count;

    public BlockBuffer() {
        this.values = new Long2ObjectOpenHashMap<>();
    }

    public BlockBuffer(Long2ObjectMap<PalettedContainer<BlockState>> values) {
        this.values = values;
    }

    public int getCount() {
        return this.count;
    }

    public void save(FriendlyByteBuf friendlyByteBuf) {
        for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : this.entrySet()) {
            friendlyByteBuf.writeLong(entry.getLongKey());
            entry.getValue().write(friendlyByteBuf);
        }

        friendlyByteBuf.writeLong(AxiomPaper.MIN_POSITION_LONG);
    }

    public static BlockBuffer load(FriendlyByteBuf friendlyByteBuf) {
        BlockBuffer buffer = new BlockBuffer();

        while (true) {
            long index = friendlyByteBuf.readLong();
            if (index == AxiomPaper.MIN_POSITION_LONG) break;

            PalettedContainer<BlockState> palettedContainer = buffer.getOrCreateSection(index);
            palettedContainer.read(friendlyByteBuf);
        }

        return buffer;
    }

    public void clear() {
        this.last = null;
        this.lastId = AxiomPaper.MIN_POSITION_LONG;
        this.values.clear();
    }

    public BlockState get(int x, int y, int z) {
        var container = this.getSectionForCoord(x, y, z);
        if (container == null) {
            return null;
        }

        var state = container.get(x & 0xF, y & 0xF, z & 0xF);
        if (state == EMPTY_STATE) {
            return null;
        } else {
            return state;
        }
    }

    public void set(int x, int y, int z, BlockState state) {
        var container = this.getOrCreateSectionForCoord(x, y, z);
        var old = container.getAndSet(x & 0xF, y & 0xF, z & 0xF, state);

        if (old == EMPTY_STATE) {
            if (state != EMPTY_STATE) this.count += 1;
        } else if (state == EMPTY_STATE) {
            this.count -= 1;
        }
    }

    public void set(int cx, int cy, int cz, int lx, int ly, int lz, BlockState state) {
        var container = this.getOrCreateSection(BlockPos.asLong(cx, cy, cz));
        var old = container.getAndSet(lx, ly, lz, state);

        if (old == EMPTY_STATE) {
            if (state != EMPTY_STATE) this.count += 1;
        } else if (state == EMPTY_STATE) {
            this.count -= 1;
        }
    }

    public BlockState remove(int x, int y, int z) {
        var container = this.getSectionForCoord(x, y, z);
        if (container == null) {
            return null;
        }

        var state = container.get(x & 0xF, y & 0xF, z & 0xF);
        if (state == EMPTY_STATE) {
            return null;
        } else {
            container.set(x & 0xF, y & 0xF, z & 0xF, EMPTY_STATE);
            return state;
        }
    }

    public ObjectSet<Long2ObjectMap.Entry<PalettedContainer<BlockState>>> entrySet() {
        return this.values.long2ObjectEntrySet();
    }

    public PalettedContainer<BlockState> getSectionForCoord(int x, int y, int z) {
        long id = BlockPos.asLong(x >> 4, y >> 4, z >> 4);

        if (id != this.lastId) {
            this.lastId = id;
            this.last = this.values.get(id);
        }

        return this.last;
    }

    public PalettedContainer<BlockState> getOrCreateSectionForCoord(int x, int y, int z) {
        long id = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
        return this.getOrCreateSection(id);
    }

    public PalettedContainer<BlockState> getOrCreateSection(long id) {
        if (this.last == null || id != this.lastId) {
            this.lastId = id;
            this.last = this.values.computeIfAbsent(id, k -> new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                             EMPTY_STATE, PalettedContainer.Strategy.SECTION_STATES));
        }

        return this.last;
    }

}
