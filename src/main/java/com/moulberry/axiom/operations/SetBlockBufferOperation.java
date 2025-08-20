package com.moulberry.axiom.operations;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.AxiomReflection;
import com.moulberry.axiom.WorldExtension;
import com.moulberry.axiom.buffer.BlockBuffer;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import com.moulberry.axiom.integration.coreprotect.CoreProtectIntegration;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.storage.TagValueInput;
import org.bukkit.Chunk;
import org.bukkit.craftbukkit.CraftChunk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SetBlockBufferOperation implements PendingOperation {

    private static final int MAX_CHUNK_FUTURES = 256;
    private final ServerPlayer player;
    private final BlockBuffer buffer;
    private final boolean allowNbt;

    private Long2ObjectOpenHashMap<List<Long2ObjectMap.Entry<PalettedContainer<BlockState>>>> sectionsForChunks = null;
    private LongArrayList getChunkFutures = null;
    private List<CompletableFuture<LevelChunk>> chunkFutures = new ArrayList<>();
    private boolean sendGameMasterBlockWarning = false;
    private boolean finished = false;

    public SetBlockBufferOperation(ServerPlayer player, BlockBuffer buffer, boolean allowNbt) {
        this.player = player;
        this.buffer = buffer;
        this.allowNbt = allowNbt;
    }

    @Override
    public boolean isFinished() {
        return this.finished;
    }

    @Override
    public ServerPlayer executor() {
        return this.player;
    }

    @Override
    public void tick(ServerLevel level) {
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        WorldExtension extension = WorldExtension.get(level);

        BlockState emptyState = BlockBuffer.EMPTY_STATE;

        if (this.sectionsForChunks == null) {
            this.sectionsForChunks = new Long2ObjectOpenHashMap<>();

            for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : this.buffer.entrySet()) {
                long pos = entry.getLongKey();
                int posX = BlockPos.getX(pos);
                int posZ = BlockPos.getZ(pos);

                long chunkPos = ChunkPos.asLong(posX, posZ);
                this.sectionsForChunks.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(entry);
            }

            this.getChunkFutures = new LongArrayList(this.sectionsForChunks.keySet());
            this.getChunkFutures.sort(LongComparators.NATURAL_COMPARATOR);
        }

        if (!this.getChunkFutures.isEmpty()) {
            int maxChunkLoadDistance = AxiomPaper.PLUGIN.getMaxChunkLoadDistance(level.getWorld());
            int playerSectionX = this.player.getBlockX() >> 4;
            int playerSectionZ = this.player.getBlockZ() >> 4;

            LongIterator newFutureIterator = this.getChunkFutures.longIterator();
            while (this.chunkFutures.size() < MAX_CHUNK_FUTURES && newFutureIterator.hasNext()) {
                long chunkPos = newFutureIterator.nextLong();
                newFutureIterator.remove();

                int x = ChunkPos.getX(chunkPos);
                int z = ChunkPos.getZ(chunkPos);

                int distance = Math.abs(playerSectionX - x) + Math.abs(playerSectionZ - z);
                boolean canLoad = distance < maxChunkLoadDistance;

                if (!canLoad) {
                    LevelChunk chunk = level.getChunkIfLoaded(x, z);
                    if (chunk != null) {
                        this.chunkFutures.add(CompletableFuture.completedFuture(chunk));
                    }
                } else {
                    this.chunkFutures.add(level.getWorld().getChunkAtAsync(x, z).thenApply(chunk -> (LevelChunk) ((CraftChunk)chunk).getHandle(ChunkStatus.FULL)));
                }
            }
        }

        Iterator<CompletableFuture<LevelChunk>> chunkFutureIterator = this.chunkFutures.iterator();
        while (chunkFutureIterator.hasNext()) {
            CompletableFuture<LevelChunk> future = chunkFutureIterator.next();
            if (!future.isDone()) {
                return;
            }

            chunkFutureIterator.remove();

            LevelChunk chunk = future.join();

            Heightmap worldSurface = null;
            Heightmap oceanFloor = null;
            Heightmap motionBlocking = null;
            Heightmap motionBlockingNoLeaves = null;
            for (Map.Entry<Heightmap.Types, Heightmap> heightmap : chunk.getHeightmaps()) {
                switch (heightmap.getKey()) {
                    case WORLD_SURFACE -> worldSurface = heightmap.getValue();
                    case OCEAN_FLOOR -> oceanFloor = heightmap.getValue();
                    case MOTION_BLOCKING -> motionBlocking = heightmap.getValue();
                    case MOTION_BLOCKING_NO_LEAVES -> motionBlockingNoLeaves = heightmap.getValue();
                    default -> {}
                }
            }

            boolean chunkChanged = false;
            boolean chunkLightChanged = false;

            long chunkPosLong = ChunkPos.asLong(chunk.locX, chunk.locZ);
            List<Long2ObjectMap.Entry<PalettedContainer<BlockState>>> sections = this.sectionsForChunks.get(chunkPosLong);
            for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : sections) {
                int cx = BlockPos.getX(entry.getLongKey());
                int cy = BlockPos.getY(entry.getLongKey());
                int cz = BlockPos.getZ(entry.getLongKey());
                PalettedContainer<BlockState> container = entry.getValue();

                if (cy < level.getMinSectionY() || cy > level.getMaxSectionY()) {
                    continue;
                }

                SectionPermissionChecker checker = Integration.checkSection(player.getBukkitEntity(), level.getWorld(), cx, cy, cz);
                if (checker != null && checker.noneAllowed()) {
                    continue;
                }

                LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(cy));
                boolean hasOnlyAir = section.hasOnlyAir();

                boolean containerMaybeHasPoi = container.maybeHas(PoiTypes::hasPoi);
                boolean sectionMaybeHasPoi = section.maybeHas(PoiTypes::hasPoi);

                Short2ObjectMap<CompressedBlockEntity> blockEntityChunkMap = this.allowNbt ? this.buffer.getBlockEntityChunkMap(entry.getLongKey()) : null;

                int minX = 0;
                int minY = 0;
                int minZ = 0;
                int maxX = 15;
                int maxY = 15;
                int maxZ = 15;

                if (checker != null) {
                    minX = checker.bounds().minX();
                    minY = checker.bounds().minY();
                    minZ = checker.bounds().minZ();
                    maxX = checker.bounds().maxX();
                    maxY = checker.bounds().maxY();
                    maxZ = checker.bounds().maxZ();
                    if (checker.allAllowed()) {
                        checker = null;
                    }
                }

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockState blockState = container.get(x, y, z);
                            if (blockState == emptyState) continue;

                            int bx = cx*16 + x;
                            int by = cy*16 + y;
                            int bz = cz*16 + z;

                            if (hasOnlyAir && blockState.isAir()) {
                                continue;
                            }

                            if (checker != null && !checker.allowed(x, y, z)) continue;

                            Block block = blockState.getBlock();

                            BlockState old = section.setBlockState(x, y, z, blockState, true);
                            if (blockState != old) {
                                chunkChanged = true;
                                blockPos.set(bx, by, bz);

                                motionBlocking.update(x, by, z, blockState);
                                motionBlockingNoLeaves.update(x, by, z, blockState);
                                oceanFloor.update(x, by, z, blockState);
                                worldSurface.update(x, by, z, blockState);

                                // Update Light
                                chunkLightChanged |= LightEngine.hasDifferentLightProperties(old, blockState);

                                // Update Poi
                                Optional<Holder<PoiType>> newPoi = containerMaybeHasPoi ? PoiTypes.forState(blockState) : Optional.empty();
                                Optional<Holder<PoiType>> oldPoi = sectionMaybeHasPoi ? PoiTypes.forState(old) : Optional.empty();
                                if (!Objects.equals(oldPoi, newPoi)) {
                                    if (oldPoi.isPresent()) level.getPoiManager().remove(blockPos);
                                    if (newPoi.isPresent()) level.getPoiManager().add(blockPos, newPoi.get());
                                }
                            }

                            if (blockState.hasBlockEntity()) {
                                blockPos.set(bx, by, bz);

                                BlockEntity blockEntity = chunk.getBlockEntity(blockPos, LevelChunk.EntityCreationType.CHECK);

                                if (blockEntity == null) {
                                    // There isn't a block entity here, create it!
                                    blockEntity = ((EntityBlock)block).newBlockEntity(blockPos, blockState);
                                    if (blockEntity != null) {
                                        chunk.addAndRegisterBlockEntity(blockEntity);
                                    }
                                } else if (blockEntity.getType().isValid(blockState)) {
                                    // Block entity is here and the type is correct
                                    blockEntity.setBlockState(blockState);
                                    AxiomReflection.updateBlockEntityTicker(chunk, blockEntity);
                                } else {
                                    // Block entity type isn't correct, we need to recreate it
                                    chunk.removeBlockEntity(blockPos);

                                    blockEntity = ((EntityBlock)block).newBlockEntity(blockPos, blockState);
                                    if (blockEntity != null) {
                                        chunk.addAndRegisterBlockEntity(blockEntity);
                                    }
                                }
                                if (blockEntity != null && blockEntityChunkMap != null) {
                                    if (blockEntity instanceof GameMasterBlock && !player.hasPermissions(2)) {
                                        sendGameMasterBlockWarning = true;
                                    } else {
                                        int key = x | (y << 4) | (z << 8);
                                        CompressedBlockEntity savedBlockEntity = blockEntityChunkMap.get((short) key);
                                        if (savedBlockEntity != null) {
                                            var input = TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), savedBlockEntity.decompress());
                                            blockEntity.loadWithComponents(input);
                                            chunkChanged = true;
                                        }
                                    }
                                }
                            } else if (old.hasBlockEntity()) {
                                chunk.removeBlockEntity(blockPos);
                            }

                            if (CoreProtectIntegration.isEnabled() && old != blockState) {
                                String changedBy = player.getBukkitEntity().getName();
                                BlockPos changedPos = new BlockPos(bx, by, bz);

                                CoreProtectIntegration.logRemoval(changedBy, old, level.getWorld(), changedPos);
                                CoreProtectIntegration.logPlacement(changedBy, blockState, level.getWorld(), changedPos);
                            }
                        }
                    }
                }

                boolean nowHasOnlyAir = section.hasOnlyAir();
                if (hasOnlyAir != nowHasOnlyAir) {
                    level.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(cx, cy, cz), nowHasOnlyAir);
                    level.getChunkSource().onSectionEmptinessChanged(cx, cy, cz, nowHasOnlyAir);
                }
            }

            if (chunkChanged) {
                extension.sendChunk(chunk.locX, chunk.locZ);
                chunk.markUnsaved();
            }
            if (chunkLightChanged) {
                extension.lightChunk(chunk.locX, chunk.locZ);
            }
        }

        if (!this.getChunkFutures.isEmpty()) {
            return;
        }

        if (this.sendGameMasterBlockWarning) {
            this.player.sendSystemMessage(Component.literal("Unable to set data for Game Master block since you don't have op").withStyle(ChatFormatting.RED));
        }

        this.finished = true;
    }
}
