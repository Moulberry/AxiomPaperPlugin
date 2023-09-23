package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.buffer.BiomeBuffer;
import com.moulberry.axiom.buffer.BlockBuffer;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.event.AxiomModifyWorldEvent;
import com.moulberry.axiom.integration.RegionProtection;
import com.moulberry.axiom.integration.SectionProtection;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class SetBlockBufferPacketListener {

    private final AxiomPaper plugin;
    private final Method updateBlockEntityTicker;

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

    public boolean onReceive(ServerPlayer player, FriendlyByteBuf friendlyByteBuf) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        ResourceKey<Level> worldKey = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        friendlyByteBuf.readUUID(); // Discard, we don't need to associate buffers
        boolean continuation = friendlyByteBuf.readBoolean();

        if (!continuation) {
            friendlyByteBuf.readNbt(); // Discard sourceInfo
        }

        byte type = friendlyByteBuf.readByte();
        if (type == 0) {
            BlockBuffer buffer = BlockBuffer.load(friendlyByteBuf);
            applyBlockBuffer(player, server, buffer, worldKey);
        } else if (type == 1) {
            BiomeBuffer buffer = BiomeBuffer.load(friendlyByteBuf);
            applyBiomeBuffer(server, buffer, worldKey);
        } else {
            throw new RuntimeException("Unknown buffer type: " + type);
        }

        return true;
    }

    private void applyBlockBuffer(ServerPlayer player, MinecraftServer server, BlockBuffer buffer, ResourceKey<Level> worldKey) {
        server.execute(() -> {
            ServerLevel world = server.getLevel(worldKey);
            if (world == null) return;

            // Call AxiomModifyWorldEvent event
            AxiomModifyWorldEvent modifyWorldEvent = new AxiomModifyWorldEvent(player.getBukkitEntity(), world.getWorld());
            Bukkit.getPluginManager().callEvent(modifyWorldEvent);
            if (modifyWorldEvent.isCancelled()) return;

            RegionProtection regionProtection = new RegionProtection(player.getBukkitEntity(), world.getWorld());

            // Allowed, apply buffer
            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

            var lightEngine = world.getChunkSource().getLightEngine();

            BlockState emptyState = BlockBuffer.EMPTY_STATE;

            for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : buffer.entrySet()) {
                int cx = BlockPos.getX(entry.getLongKey());
                int cy = BlockPos.getY(entry.getLongKey());
                int cz = BlockPos.getZ(entry.getLongKey());
                PalettedContainer<BlockState> container = entry.getValue();

                if (cy < world.getMinSection() || cy >= world.getMaxSection()) {
                    continue;
                }

                SectionProtection sectionProtection = regionProtection.getSection(cx, cy, cz);
//                switch (sectionProtection.getSectionState()) {
//                    case ALLOW -> sectionProtection = null;
//                    case DENY -> {
//                        continue;
//                    }
//                    case CHECK -> {}
//                }

                LevelChunk chunk = world.getChunk(cx, cz);
                chunk.setUnsaved(true);

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

                Short2ObjectMap<CompressedBlockEntity> blockEntityChunkMap = buffer.getBlockEntityChunkMap(entry.getLongKey());

                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState blockState = container.get(x, y, z);
                            if (blockState == emptyState) continue;

                            switch (sectionProtection.getSectionState()) {
                                case ALLOW -> {}
                                case DENY -> blockState = Blocks.REDSTONE_BLOCK.defaultBlockState();
                                case CHECK -> blockState = Blocks.DIAMOND_BLOCK.defaultBlockState();
                            }

                            int bx = cx*16 + x;
                            int by = cy*16 + y;
                            int bz = cz*16 + z;

//                          if (!regionProtection.canBuild(bx, by, bz)) {
//                              continue;
//                          }

                            blockPos.set(bx, by, bz);

                            if (hasOnlyAir && blockState.isAir()) {
                                continue;
                            }

                            BlockState old = section.setBlockState(x, y, z, blockState, true);
                            if (blockState != old) {
                                Block block = blockState.getBlock();
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

                                if (blockState.hasBlockEntity()) {
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

                                world.getChunkSource().blockChanged(blockPos); // todo: maybe simply resend chunk instead of this?

                                if (LightEngine.hasDifferentLightProperties(chunk, blockPos, old, blockState)) {
                                    lightEngine.checkBlock(blockPos);
                                }
                            }
                        }
                    }
                }

                boolean nowHasOnlyAir = section.hasOnlyAir();
                if (hasOnlyAir != nowHasOnlyAir) {
                    world.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(cx, cy, cz), nowHasOnlyAir);
                }
            }
        });
    }


    private void applyBiomeBuffer(MinecraftServer server, BiomeBuffer biomeBuffer, ResourceKey<Level> worldKey) {
        server.execute(() -> {
            ServerLevel world = server.getLevel(worldKey);
            if (world == null) return;

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

                var chunk = (LevelChunk) world.getChunk(x >> 2, z >> 2, ChunkStatus.FULL, false);
                if (chunk == null) return;

                var section = chunk.getSection(cy - minSection);
                PalettedContainer<Holder<Biome>> container = (PalettedContainer<Holder<Biome>>) section.getBiomes();

                var holder = registry.getHolder(biome);
                if (holder.isPresent()) {
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
        });
    }

}
