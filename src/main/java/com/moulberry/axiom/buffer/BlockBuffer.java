package com.moulberry.axiom.buffer;

import com.google.common.util.concurrent.RateLimiter;
import com.mojang.serialization.Codec;
import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.viaversion.UnknownVersionHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMap;
import net.minecraft.core.IdMapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class BlockBuffer {

    public static final BlockState EMPTY_STATE = Blocks.VOID_AIR.defaultBlockState();

    private static final Map<BlockState, Codec<PalettedContainer<BlockState>>> BLOCK_STATE_CODECS = new HashMap<>();
    private static final Map<BlockState, IdMap<BlockState>> ID_MAPPERS = new HashMap<>();

    public static PalettedContainer<BlockState> createPalettedContainerForEmptyBlockState(BlockState emptyBlockState) {
        return new PalettedContainer<>(BlockBuffer.getIdMapForEmptyBlockState(emptyBlockState), EMPTY_STATE, PalettedContainer.Strategy.SECTION_STATES);
    }

    public static IdMap<BlockState> getIdMapForEmptyBlockState(BlockState empty) {
        if (empty == EMPTY_STATE) {
            return Block.BLOCK_STATE_REGISTRY;
        }
        return ID_MAPPERS.computeIfAbsent(empty, emptyState -> {
            IdMapper<BlockState> mapper = new IdMapper<>(Block.BLOCK_STATE_REGISTRY.size());
            for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
                mapper.addMapping(blockState, Block.BLOCK_STATE_REGISTRY.getId(blockState));
            }
            mapper.addMapping(EMPTY_STATE, Block.BLOCK_STATE_REGISTRY.getId(emptyState));
            mapper.addMapping(EMPTY_STATE, Block.BLOCK_STATE_REGISTRY.getId(EMPTY_STATE));
            return mapper;
        });
    }

    public static Codec<PalettedContainer<BlockState>> getCodecForEmptyBlockState(BlockState empty) {
        return BLOCK_STATE_CODECS.computeIfAbsent(empty, emptyState -> {
            IdMap<BlockState> mapping = getIdMapForEmptyBlockState(emptyState);
            Codec<BlockState> blockStateCodec;

            if (emptyState == EMPTY_STATE) {
                blockStateCodec = BlockState.CODEC;
            } else {
                Function<BlockState, BlockState> mapFunction = blockState -> {
                    if (blockState == emptyState) {
                        return EMPTY_STATE;
                    } else {
                        return blockState;
                    }
                };
                blockStateCodec = BlockState.CODEC.xmap(mapFunction, mapFunction);
            }

            return PalettedContainer.codecRW(mapping, blockStateCodec, PalettedContainer.Strategy.SECTION_STATES, EMPTY_STATE);
        });
    }

    private final Long2ObjectMap<PalettedContainer<BlockState>> values;

    private IdMapper<BlockState> registry;
    private PalettedContainer<BlockState> last = null;
    private long lastId = AxiomConstants.MIN_POSITION_LONG;
    private final Long2ObjectMap<Short2ObjectMap<CompressedBlockEntity>> blockEntities = new Long2ObjectOpenHashMap<>();
    private long totalBlockEntities = 0;
    private long totalBlockEntityBytes = 0;

    public BlockBuffer(IdMapper<BlockState> registry) {
        this.values = new Long2ObjectOpenHashMap<>();
        this.registry = registry;
    }

    public static BlockBuffer load(FriendlyByteBuf friendlyByteBuf, IdMapper<BlockState> registry, Player player) {
        BlockBuffer buffer = new BlockBuffer(registry);

        long totalBlockEntities = 0;
        long totalBlockEntityBytes = 0;

        while (true) {
            long index = friendlyByteBuf.readLong();
            if (index == AxiomConstants.MIN_POSITION_LONG) break;

            PalettedContainer<BlockState> palettedContainer = buffer.getOrCreateSection(index);
            UnknownVersionHelper.readPalettedContainerUnknown(friendlyByteBuf, palettedContainer, player);

            int blockEntitySize = Math.min(4096, friendlyByteBuf.readVarInt());
            if (blockEntitySize > 0) {
                Short2ObjectMap<CompressedBlockEntity> map = new Short2ObjectOpenHashMap<>(blockEntitySize);

                int startIndex = friendlyByteBuf.readerIndex();

                for (int i = 0; i < blockEntitySize; i++) {
                    short offset = friendlyByteBuf.readShort();
                    CompressedBlockEntity blockEntity = CompressedBlockEntity.read(friendlyByteBuf);
                    map.put(offset, blockEntity);
                }

                buffer.blockEntities.put(index, map);
                totalBlockEntities += blockEntitySize;
                totalBlockEntityBytes += friendlyByteBuf.readerIndex() - startIndex;
            }
        }

        buffer.totalBlockEntities = totalBlockEntities;
        buffer.totalBlockEntityBytes = totalBlockEntityBytes;
        return buffer;
    }

    public long getTotalBlockEntities() {
        return this.totalBlockEntities;
    }

    public long getTotalBlockEntityBytes() {
        return this.totalBlockEntityBytes;
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

    public int getSectionCount() {
        return this.values.size();
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
            this.last = this.values.computeIfAbsent(id, k -> new PalettedContainer<>(this.registry,
                             EMPTY_STATE, PalettedContainer.Strategy.SECTION_STATES));
        }

        return this.last;
    }

}
