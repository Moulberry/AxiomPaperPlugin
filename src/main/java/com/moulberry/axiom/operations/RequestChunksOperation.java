package com.moulberry.axiom.operations;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.packet.impl.RequestChunkDataPacketListener;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.bukkit.Chunk;
import org.bukkit.craftbukkit.v1_20_R2.CraftChunk;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RequestChunksOperation implements PendingOperation {

    private static final int MAX_CHUNK_FUTURES = 256;
    private boolean finished = false;

    private final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

    private final ServerPlayer serverPlayer;
    private final long id;

    private final LongArrayList getChunkFutures;
    private  List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();
    private final Long2ObjectMap<LongList> sendBlockEntityForPendingChunks;
    private final Long2ObjectMap<IntList> sendSectionsForPendingChunks;
    private final boolean sendBlockEntitiesInChunks;

    private final Long2ObjectOpenHashMap<PalettedContainer<BlockState>> sendingSections;
    private final Long2ObjectOpenHashMap<CompressedBlockEntity> sendingBlockEntities;
    private final ByteArrayOutputStream baos;

    public RequestChunksOperation(ServerPlayer serverPlayer, long id, LongSet chunkFutures, Long2ObjectMap<LongList> sendBlockEntityForPendingChunks, Long2ObjectMap<IntList> sendSectionsForPendingChunks, boolean sendBlockEntitiesInChunks, Long2ObjectOpenHashMap<PalettedContainer<BlockState>> sendingSections, Long2ObjectOpenHashMap<CompressedBlockEntity> sendingBlockEntities, ByteArrayOutputStream baos) {
        this.serverPlayer = serverPlayer;
        this.id = id;
        this.sendBlockEntityForPendingChunks = sendBlockEntityForPendingChunks;
        this.sendSectionsForPendingChunks = sendSectionsForPendingChunks;
        this.sendBlockEntitiesInChunks = sendBlockEntitiesInChunks;
        this.sendingSections = sendingSections;
        this.sendingBlockEntities = sendingBlockEntities;
        this.baos = baos;

        LongArrayList getChunkFutures = new LongArrayList(chunkFutures);
        getChunkFutures.unstableSort(LongComparators.NATURAL_COMPARATOR);
        this.getChunkFutures = getChunkFutures;
    }

    @Override
    public boolean isFinished() {
        return this.finished;
    }

    @Override
    public ServerPlayer executor() {
        return this.serverPlayer;
    }

    @Override
    public void tick(ServerLevel level) {
        if (this.finished) {
            return;
        }

        if (this.serverPlayer.hasDisconnected()) {
            this.finished = true;
            return;
        }

        if (!this.getChunkFutures.isEmpty()) {
            int count = this.chunkFutures.size();
            LongIterator newFutureIterator = this.getChunkFutures.longIterator();
            while (count++ < MAX_CHUNK_FUTURES && newFutureIterator.hasNext()) {
                long chunkPos = newFutureIterator.nextLong();
                newFutureIterator.remove();

                int x = ChunkPos.getX(chunkPos);
                int z = ChunkPos.getZ(chunkPos);
                this.chunkFutures.add(level.getWorld().getChunkAtAsync(x, z));
            }
        }

        Iterator<CompletableFuture<Chunk>> chunkFutureIterator = this.chunkFutures.iterator();
        while (chunkFutureIterator.hasNext()) {
            CompletableFuture<Chunk> future = chunkFutureIterator.next();
            if (!future.isDone()) {
                return;
            }

            chunkFutureIterator.remove();

            LevelChunk chunk = (LevelChunk) ((CraftChunk)future.join()).getHandle(ChunkStatus.FULL);
            long chunkPosLong = ChunkPos.asLong(chunk.locX, chunk.locZ);
            LongList blockEntitiesInChunk = this.sendBlockEntityForPendingChunks.get(chunkPosLong);
            if (blockEntitiesInChunk != null) {
                LongIterator iterator = blockEntitiesInChunk.longIterator();
                while (iterator.hasNext()) {
                    long blockEntityPos = iterator.nextLong();
                    this.mutableBlockPos.set(blockEntityPos);

                    BlockEntity blockEntity = chunk.getBlockEntity(this.mutableBlockPos, LevelChunk.EntityCreationType.CHECK);
                    if (blockEntity != null) {
                        CompoundTag tag = blockEntity.saveWithoutMetadata();
                        this.sendingBlockEntities.put(blockEntityPos, CompressedBlockEntity.compress(tag, baos));
                    }
                }
            }

            IntList sendSectionsInChunk = this.sendSectionsForPendingChunks.get(chunkPosLong);
            if (sendSectionsInChunk != null) {
                boolean hasNonAirSectionInChunk = false;

                IntIterator sectionIterator = sendSectionsInChunk.intIterator();
                while (sectionIterator.hasNext()) {
                    int sy = sectionIterator.nextInt();

                    int sectionIndex = chunk.getSectionIndexFromSectionY(sy);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) continue;
                    LevelChunkSection section = chunk.getSection(sectionIndex);

                    if (section.hasOnlyAir()) {
                        this.sendingSections.put(BlockPos.asLong(chunk.locX, sy, chunk.locZ), null);
                    } else {
                        PalettedContainer<BlockState> container = section.getStates();
                        this.sendingSections.put(BlockPos.asLong(chunk.locX, sy, chunk.locZ), container);
                        hasNonAirSectionInChunk = true;
                    }
                }

                if (this.sendBlockEntitiesInChunks && hasNonAirSectionInChunk) {
                    Set<Map.Entry<BlockPos, BlockEntity>> entrySet = chunk.blockEntities.entrySet();
                    Iterator<Map.Entry<BlockPos, BlockEntity>> iterator;
                    if (entrySet instanceof Object2ObjectMap.FastEntrySet fastEntrySet) {
                        iterator = fastEntrySet.fastIterator();
                    } else {
                        iterator = entrySet.iterator();
                    }

                    while (iterator.hasNext()) {
                        Map.Entry<BlockPos, BlockEntity> entry = iterator.next();

                        BlockPos blockPos = entry.getKey();
                        int sectionY = blockPos.getY() >> 4;
                        if (!sendSectionsInChunk.contains(sectionY)) {
                            continue;
                        }

                        CompoundTag tag = entry.getValue().saveWithoutMetadata();
                        this.sendingBlockEntities.put(blockPos.asLong(), CompressedBlockEntity.compress(tag, baos));
                    }
                }
            }
        }

        if (!this.getChunkFutures.isEmpty()) {
            return;
        }

        RequestChunkDataPacketListener.sendResponse(this.serverPlayer, this.id, this.sendingBlockEntities, this.sendingSections);
        this.finished = true;
    }


}
