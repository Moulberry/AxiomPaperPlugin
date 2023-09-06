package com.moulberry.axiom.buffer;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.AxiomPaper;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jetbrains.annotations.Nullable;

public class BlockBuffer {

    public static final BlockState EMPTY_STATE = Blocks.STRUCTURE_VOID.defaultBlockState();

    private final Long2ObjectMap<PalettedContainer<BlockState>> values;

    private PalettedContainer<BlockState> last = null;
    private long lastId = AxiomConstants.MIN_POSITION_LONG;
    private final Long2ObjectMap<Short2ObjectMap<CompressedBlockEntity>> blockEntities = new Long2ObjectOpenHashMap<>();

    public BlockBuffer() {
        this.values = new Long2ObjectOpenHashMap<>();
    }

    public BlockBuffer(Long2ObjectMap<PalettedContainer<BlockState>> values) {
        this.values = values;
    }

    public void save(FriendlyByteBuf friendlyByteBuf) {
        for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : this.entrySet()) {
            friendlyByteBuf.writeLong(entry.getLongKey());
            entry.getValue().write(friendlyByteBuf);

            Short2ObjectMap<CompressedBlockEntity> blockEntities = this.blockEntities.get(entry.getLongKey());
            if (blockEntities != null) {
                friendlyByteBuf.writeVarInt(blockEntities.size());
                for (Short2ObjectMap.Entry<CompressedBlockEntity> entry2 : blockEntities.short2ObjectEntrySet()) {
                    friendlyByteBuf.writeShort(entry2.getShortKey());
                    entry2.getValue().write(friendlyByteBuf);
                }
            } else {
                friendlyByteBuf.writeVarInt(0);
            }
        }

        friendlyByteBuf.writeLong(AxiomConstants.MIN_POSITION_LONG);
    }

    public static BlockBuffer load(FriendlyByteBuf friendlyByteBuf) {
        BlockBuffer buffer = new BlockBuffer();

        while (true) {
            long index = friendlyByteBuf.readLong();
            if (index == AxiomConstants.MIN_POSITION_LONG) break;

            PalettedContainer<BlockState> palettedContainer = buffer.getOrCreateSection(index);
            palettedContainer.read(friendlyByteBuf);

            int blockEntitySize = Math.min(4096, friendlyByteBuf.readVarInt());
            if (blockEntitySize > 0) {
                Short2ObjectMap<CompressedBlockEntity> map = new Short2ObjectOpenHashMap<>(blockEntitySize);
                for (int i = 0; i < blockEntitySize; i++) {
                    short offset = friendlyByteBuf.readShort();
                    CompressedBlockEntity blockEntity = CompressedBlockEntity.read(friendlyByteBuf);
                    map.put(offset, blockEntity);
                }
                buffer.blockEntities.put(index, map);
            }
        }

        return buffer;
    }

    public void clear() {
        this.last = null;
        this.lastId = AxiomConstants.MIN_POSITION_LONG;
        this.values.clear();
    }

    public void putBlockEntity(int x, int y, int z, CompressedBlockEntity blockEntity) {
        long cpos = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
        Short2ObjectMap<CompressedBlockEntity> chunkMap = this.blockEntities.computeIfAbsent(cpos, k -> new Short2ObjectOpenHashMap<>());

        int key = (x & 0xF) | ((y & 0xF) << 4) | ((z & 0xF) << 8);
        chunkMap.put((short)key, blockEntity);
    }

    @Nullable
    public CompressedBlockEntity getBlockEntity(int x, int y, int z) {
        long cpos = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
        Short2ObjectMap<CompressedBlockEntity> chunkMap = this.blockEntities.get(cpos);

        if (chunkMap == null) return null;

        int key = (x & 0xF) | ((y & 0xF) << 4) | ((z & 0xF) << 8);
        return chunkMap.get((short)key);
    }

    @Nullable
    public Short2ObjectMap<CompressedBlockEntity> getBlockEntityChunkMap(long cpos) {
        return this.blockEntities.get(cpos);
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
    }

    public void set(int cx, int cy, int cz, int lx, int ly, int lz, BlockState state) {
        var container = this.getOrCreateSection(BlockPos.asLong(cx, cy, cz));
        var old = container.getAndSet(lx, ly, lz, state);
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
