package com.moulberry.axiom.packet;

import com.google.common.util.concurrent.RateLimiter;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.WorldExtension;
import com.moulberry.axiom.buffer.BiomeBuffer;
import com.moulberry.axiom.buffer.BlockBuffer;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import com.moulberry.axiom.integration.coreprotect.CoreProtectIntegration;
import com.moulberry.axiom.viaversion.UnknownVersionHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import org.bukkit.Location;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SetBlockBufferPacketListener {

    private final AxiomPaper plugin;
    private final Method updateBlockEntityTicker;
    private final WeakHashMap<ServerPlayer, RateLimiter> packetRateLimiter = new WeakHashMap<>();

    public SetBlockBufferPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;

        ReflectionRemapper reflectionRemapper = ReflectionRemapper.forReobfMappingsInPaperJar();
        String methodName = reflectionRemapper.remapMethodName(LevelChunk.class, "updateBlockEntityTicker", BlockEntity.class);

        try {
            this.updateBlockEntityTicker = LevelChunk.class.getDeclaredMethod(methodName, BlockEntity.class);
            this.updateBlockEntityTicker.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void onReceive(ServerPlayer player, FriendlyByteBuf friendlyByteBuf) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ResourceKey<Level> worldKey = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        friendlyByteBuf.readUUID(); // Discard, we don't need to associate buffers
        boolean continuation = friendlyByteBuf.readBoolean();

        if (!continuation) {
            UnknownVersionHelper.skipTagUnknown(friendlyByteBuf, player.getBukkitEntity());
        }

        RateLimiter rateLimiter = this.plugin.getBlockBufferRateLimiter(player.getUUID());

        byte type = friendlyByteBuf.readByte();
        if (type == 0) {
            AtomicBoolean reachedRateLimit = new AtomicBoolean(false);
            BlockBuffer buffer = BlockBuffer.load(friendlyByteBuf, rateLimiter, reachedRateLimit, this.plugin.getBlockRegistry(player.getUUID()));
            if (reachedRateLimit.get()) {
                player.sendSystemMessage(Component.literal("[Axiom] Exceeded server rate-limit of " + (int)rateLimiter.getRate() + " sections per second")
                    .withStyle(ChatFormatting.RED));
            }

            if (this.plugin.logLargeBlockBufferChanges()) {
                this.plugin.getLogger().info("Player " + player.getUUID() + " modified " + buffer.entrySet().size() + " chunk sections (blocks)");
                if (buffer.getTotalBlockEntities() > 0) {
                    this.plugin.getLogger().info("Player " + player.getUUID() + " modified " + buffer.getTotalBlockEntities() + " block entities, compressed bytes = " +
                        buffer.getTotalBlockEntityBytes());
                }
            }

            applyBlockBuffer(player, server, buffer, worldKey);
        } else if (type == 1) {
            AtomicBoolean reachedRateLimit = new AtomicBoolean(false);
            BiomeBuffer buffer = BiomeBuffer.load(friendlyByteBuf, rateLimiter, reachedRateLimit);
            if (reachedRateLimit.get()) {
                player.sendSystemMessage(Component.literal("[Axiom] Exceeded server rate-limit of " + (int)rateLimiter.getRate() + " sections per second")
                                                  .withStyle(ChatFormatting.RED));
            }

            if (this.plugin.logLargeBlockBufferChanges()) {
                this.plugin.getLogger().info("Player " + player.getUUID() + " modified " + buffer.size() + " chunk sections (biomes)");
            }

            applyBiomeBuffer(player, server, buffer, worldKey);
        } else {
            throw new RuntimeException("Unknown buffer type: " + type);
        }
    }

    private void applyBlockBuffer(ServerPlayer player, MinecraftServer server, BlockBuffer buffer, ResourceKey<Level> worldKey) {
        server.execute(() -> {
            try {
                ServerLevel world = player.serverLevel();
                if (!world.dimension().equals(worldKey)) return;

                if (!this.plugin.canUseAxiom(player.getBukkitEntity())) {
                    return;
                }

                if (!this.plugin.canModifyWorld(player.getBukkitEntity(), world.getWorld())) {
                    return;
                }

                // Allowed, apply buffer
                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
                WorldExtension extension = WorldExtension.get(world);

                BlockState emptyState = BlockBuffer.EMPTY_STATE;

                for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : buffer.entrySet()) {
                    int cx = BlockPos.getX(entry.getLongKey());
                    int cy = BlockPos.getY(entry.getLongKey());
                    int cz = BlockPos.getZ(entry.getLongKey());
                    PalettedContainer<BlockState> container = entry.getValue();

                    if (cy < world.getMinSection() || cy >= world.getMaxSection()) {
                        continue;
                    }

                    SectionPermissionChecker checker = Integration.checkSection(player.getBukkitEntity(), world.getWorld(), cx, cy, cz);
                    if (checker != null && checker.noneAllowed()) {
                        continue;
                    }

                    LevelChunk chunk = world.getChunk(cx, cz);

                    LevelChunkSection section = chunk.getSection(world.getSectionIndexFromSectionY(cy));
                    PalettedContainer<BlockState> sectionStates = section.getStates();
                    boolean hasOnlyAir = section.hasOnlyAir();

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

                    boolean sectionChanged = false;
                    boolean sectionLightChanged = false;

                    boolean containerMaybeHasPoi = container.maybeHas(PoiTypes::hasPoi);
                    boolean sectionMaybeHasPoi = section.maybeHas(PoiTypes::hasPoi);

                    Short2ObjectMap<CompressedBlockEntity> blockEntityChunkMap = buffer.getBlockEntityChunkMap(entry.getLongKey());

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
                                    CoreProtectIntegration.logRemoval(player.getBukkitEntity().getName(), old, world.getWorld(), bx, by, bz);
                                    CoreProtectIntegration.logPlacement(player.getBukkitEntity().getName(), blockState, world.getWorld(), bx, by, bz);

                                    sectionChanged = true;
                                    blockPos.set(bx, by, bz);

                                    motionBlocking.update(x, by, z, blockState);
                                    motionBlockingNoLeaves.update(x, by, z, blockState);
                                    oceanFloor.update(x, by, z, blockState);
                                    worldSurface.update(x, by, z, blockState);

                                    if (false) { // Full update
                                        old.onRemove(world, blockPos, blockState, false);

                                        if (sectionStates.get(x, y, z).is(block)) {
                                            blockState.onPlace(world, blockPos, old, false);
                                        }
                                    }

                                    // Update Light
                                    sectionLightChanged |= LightEngine.hasDifferentLightProperties(chunk, blockPos, old, blockState);

                                    // Update Poi
                                    Optional<Holder<PoiType>> newPoi = containerMaybeHasPoi ? PoiTypes.forState(blockState) : Optional.empty();
                                    Optional<Holder<PoiType>> oldPoi = sectionMaybeHasPoi ? PoiTypes.forState(old) : Optional.empty();
                                    if (!Objects.equals(oldPoi, newPoi)) {
                                        if (oldPoi.isPresent()) world.getPoiManager().remove(blockPos);
                                        if (newPoi.isPresent()) world.getPoiManager().add(blockPos, newPoi.get());
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

                                        try {
                                            this.updateBlockEntityTicker.invoke(chunk, blockEntity);
                                        } catch (IllegalAccessException | InvocationTargetException e) {
                                            throw new RuntimeException(e);
                                        }
                                    } else {
                                        // Block entity type isn't correct, we need to recreate it
                                        chunk.removeBlockEntity(blockPos);

                                        blockEntity = ((EntityBlock)block).newBlockEntity(blockPos, blockState);
                                        if (blockEntity != null) {
                                            chunk.addAndRegisterBlockEntity(blockEntity);
                                        }
                                    }
                                    if (blockEntity != null && blockEntityChunkMap != null) {
                                        int key = x | (y << 4) | (z << 8);
                                        CompressedBlockEntity savedBlockEntity = blockEntityChunkMap.get((short) key);
                                        if (savedBlockEntity != null) {
                                            blockEntity.load(savedBlockEntity.decompress());
                                        }
                                    }
                                } else if (old.hasBlockEntity()) {
                                    chunk.removeBlockEntity(blockPos);
                                }
                            }
                        }
                    }

                    boolean nowHasOnlyAir = section.hasOnlyAir();
                    if (hasOnlyAir != nowHasOnlyAir) {
                        world.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(cx, cy, cz), nowHasOnlyAir);
                    }

                    if (sectionChanged) {
                        extension.sendChunk(cx, cz);
                        chunk.setUnsaved(true);
                    }
                    if (sectionLightChanged) {
                        extension.lightChunk(cx, cz);
                    }
                }
            } catch (Throwable t) {
                player.getBukkitEntity().kick(net.kyori.adventure.text.Component.text("An error occured while processing block change: " + t.getMessage()));
            }
        });
    }

    private void applyBiomeBuffer(ServerPlayer player, MinecraftServer server, BiomeBuffer biomeBuffer, ResourceKey<Level> worldKey) {
        server.execute(() -> {
            try {
                ServerLevel world = player.serverLevel();
                if (!world.dimension().equals(worldKey)) return;

                if (!this.plugin.canUseAxiom(player.getBukkitEntity())) {
                    return;
                }

                if (!this.plugin.canModifyWorld(player.getBukkitEntity(), world.getWorld())) {
                    return;
                }

                Set<LevelChunk> changedChunks = new HashSet<>();

                int minSection = world.getMinSection();
                int maxSection = world.getMaxSection();

                Optional<Registry<Biome>> registryOptional = world.registryAccess().registry(Registries.BIOME);
                if (registryOptional.isEmpty()) return;

                Registry<Biome> registry = registryOptional.get();

                biomeBuffer.forEachEntry((x, y, z, biome) -> {
                    int cy = y >> 2;
                    if (cy < minSection || cy >= maxSection) {
                        return;
                    }

                    var holder = registry.getHolder(biome);
                    if (holder.isPresent()) {
                        var chunk = (LevelChunk) world.getChunk(x >> 2, z >> 2, ChunkStatus.FULL, false);
                        if (chunk == null) return;

                        var section = chunk.getSection(cy - minSection);
                        PalettedContainer<Holder<Biome>> container = (PalettedContainer<Holder<Biome>>) section.getBiomes();

                        if (!Integration.canPlaceBlock(player.getBukkitEntity(),
                            new Location(player.getBukkitEntity().getWorld(), x+1, y+1, z+1))) return;

                        container.set(x & 3, y & 3, z & 3, holder.get());
                        changedChunks.add(chunk);
                    }
                });

                var chunkMap = world.getChunkSource().chunkMap;
                HashMap<ServerPlayer, List<LevelChunk>> map = new HashMap<>();
                for (LevelChunk chunk : changedChunks) {
                    chunk.setUnsaved(true);
                    ChunkPos chunkPos = chunk.getPos();
                    for (ServerPlayer serverPlayer2 : chunkMap.getPlayers(chunkPos, false)) {
                        map.computeIfAbsent(serverPlayer2, serverPlayer -> new ArrayList<>()).add(chunk);
                    }
                }
                map.forEach((serverPlayer, list) -> serverPlayer.connection.send(ClientboundChunksBiomesPacket.forChunks(list)));
            } catch (Throwable t) {
                player.getBukkitEntity().kick(net.kyori.adventure.text.Component.text("An error occured while processing biome change: " + t.getMessage()));
            }
        });
    }

}
