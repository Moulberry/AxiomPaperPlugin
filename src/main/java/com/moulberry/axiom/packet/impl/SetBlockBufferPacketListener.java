package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.FoliaSupport;
import com.moulberry.axiom.WorldExtension;
import com.moulberry.axiom.buffer.BiomeBuffer;
import com.moulberry.axiom.buffer.BlockBuffer;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import com.moulberry.axiom.integration.coreprotect.CoreProtectIntegration;
import com.moulberry.axiom.operations.SetBlockBufferOperation;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import net.minecraft.ChatFormatting;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
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
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class SetBlockBufferPacketListener implements PacketHandler {

    private final AxiomPaper plugin;

    public SetBlockBufferPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean handleAsync() {
        return true;
    }

    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        ServerPlayer serverPlayer = ((CraftPlayer)player).getHandle();
        MinecraftServer server = serverPlayer.level().getServer();
        if (server == null) return;

        ResourceKey<Level> worldKey = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        friendlyByteBuf.readUUID(); // Discard, we don't need to associate buffers

        byte type = friendlyByteBuf.readByte();
        if (type == 0) {
            BlockBuffer buffer = BlockBuffer.load(friendlyByteBuf, this.plugin.getBlockRegistry(serverPlayer.getUUID()), serverPlayer.getBukkitEntity());
            int clientAvailableDispatchSends = friendlyByteBuf.readVarInt();

            applyBlockBuffer(serverPlayer, server, buffer, worldKey, clientAvailableDispatchSends);
        } else if (type == 1) {
            BiomeBuffer buffer = BiomeBuffer.load(friendlyByteBuf);
            int clientAvailableDispatchSends = friendlyByteBuf.readVarInt();

            applyBiomeBuffer(serverPlayer, server, buffer, worldKey, clientAvailableDispatchSends);
        } else {
            throw new RuntimeException("Unknown buffer type: " + type);
        }
    }

    private void applyBlockBuffer(ServerPlayer player, MinecraftServer server, BlockBuffer buffer, ResourceKey<Level> worldKey, int clientAvailableDispatchSends) {
        FoliaSupport.runDeferred(server, () -> {
            try {
                if (this.plugin.logLargeBlockBufferChanges()) {
                    this.plugin.getLogger().info("Player " + player.getUUID() + " modified " + buffer.getSectionCount() + " chunk sections (blocks)");
                    if (buffer.getTotalBlockEntities() > 0) {
                        this.plugin.getLogger().info("Player " + player.getUUID() + " modified " + buffer.getTotalBlockEntities() + " block entities, compressed bytes = " +
                            buffer.getTotalBlockEntityBytes());
                    }
                }

                if (!this.plugin.consumeDispatchSends(player.getBukkitEntity(), buffer.getSectionCount(), clientAvailableDispatchSends)) {
                    return;
                }

                if (!this.plugin.canUseAxiom(player.getBukkitEntity(), AxiomPermission.BUILD_SECTION)) {
                    this.plugin.reportRejectedEdit(player.getBukkitEntity(), "missing permission axiom.build.section, or Axiom is not active for you");
                    return;
                }

                ServerLevel world = player.level();
                if (!world.dimension().equals(worldKey)) {
                    this.plugin.reportRejectedEdit(player.getBukkitEntity(), "the edit was for another world than the one you are in");
                    return;
                }
                if (!this.plugin.canModifyWorld(player.getBukkitEntity(), world.getWorld())) {
                    this.plugin.reportRejectedEdit(player.getBukkitEntity(), "this world cannot be modified (world whitelist/blacklist in the config, or another plugin cancelled it)");
                    return;
                }

                boolean allowNbt = this.plugin.hasPermission(player.getBukkitEntity(), AxiomPermission.BUILD_NBT);
                this.plugin.addPendingOperation(world, new SetBlockBufferOperation(player, buffer, allowNbt));
            } catch (Throwable t) {
                player.getBukkitEntity().kick(net.kyori.adventure.text.Component.text("An error occured while processing block change: " + t.getMessage()));
            }
        });
    }

    private void applyBiomeBuffer(ServerPlayer player, MinecraftServer server, BiomeBuffer biomeBuffer, ResourceKey<Level> worldKey, int clientAvailableDispatchSends) {
        FoliaSupport.runDeferred(server, () -> {
            try {
                if (this.plugin.logLargeBlockBufferChanges()) {
                    this.plugin.getLogger().info("Player " + player.getUUID() + " modified " + biomeBuffer.getSectionCount() + " chunk sections (biomes)");
                }

                if (!this.plugin.consumeDispatchSends(player.getBukkitEntity(), biomeBuffer.getSectionCount(), clientAvailableDispatchSends)) {
                    return;
                }

                if (!this.plugin.canUseAxiom(player.getBukkitEntity(), AxiomPermission.BUILD_SECTION)) {
                    this.plugin.reportRejectedEdit(player.getBukkitEntity(), "missing permission axiom.build.section, or Axiom is not active for you");
                    return;
                }

                ServerLevel world = player.level();
                if (!world.dimension().equals(worldKey)) {
                    this.plugin.reportRejectedEdit(player.getBukkitEntity(), "the edit was for another world than the one you are in");
                    return;
                }
                if (!this.plugin.canModifyWorld(player.getBukkitEntity(), world.getWorld())) {
                    this.plugin.reportRejectedEdit(player.getBukkitEntity(), "this world cannot be modified (world whitelist/blacklist in the config, or another plugin cancelled it)");
                    return;
                }

                int minSection = world.getMinSectionY();
                int maxSection = world.getMaxSectionY();

                Optional<Registry<Biome>> registryOptional = world.registryAccess().lookup(Registries.BIOME);
                if (registryOptional.isEmpty()) return;

                Registry<Biome> registry = registryOptional.get();

                Map<Long, List<BiomeChange>> changesByChunk = new HashMap<>();

                biomeBuffer.forEachEntry((x, y, z, biome) -> {
                    int cy = y >> 2;
                    if (cy < minSection || cy > maxSection) {
                        return;
                    }

                    var holder = registry.get(biome);
                    if (holder.isPresent()) {
                        long chunkPos = ChunkPos.asLong(x >> 2, z >> 2);
                        changesByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(new BiomeChange(x, y, z, holder.get()));
                    }
                });

                for (Map.Entry<Long, List<BiomeChange>> entry : changesByChunk.entrySet()) {
                    int chunkX = ChunkPos.getX(entry.getKey());
                    int chunkZ = ChunkPos.getZ(entry.getKey());
                    List<BiomeChange> changes = entry.getValue();

                    FoliaSupport.runAtChunk(world, chunkX, chunkZ, () -> {
                        try {
                            applyBiomeChanges(player, world, chunkX, chunkZ, minSection, changes);
                        } catch (Throwable t) {
                            player.getBukkitEntity().kick(net.kyori.adventure.text.Component.text("An error occured while processing biome change: " + t.getMessage()));
                        }
                    });
                }
            } catch (Throwable t) {
                player.getBukkitEntity().kick(net.kyori.adventure.text.Component.text("An error occured while processing biome change: " + t.getMessage()));
            }
        });
    }

    private record BiomeChange(int x, int y, int z, Holder<Biome> biome) {
    }

    @SuppressWarnings("unchecked")
    private static void applyBiomeChanges(ServerPlayer player, ServerLevel world, int chunkX, int chunkZ, int minSection, List<BiomeChange> changes) {
        LevelChunk chunk = (LevelChunk) world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) return;

        boolean changed = false;

        for (BiomeChange change : changes) {
            int x = change.x();
            int y = change.y();
            int z = change.z();

            var section = chunk.getSection((y >> 2) - minSection);
            PalettedContainer<Holder<Biome>> container = (PalettedContainer<Holder<Biome>>) section.getBiomes();

            if (!Integration.canPlaceBlock(player.getBukkitEntity(),
                new Location(world.getWorld(), (x<<2)+1, (y<<2)+1, (z<<2)+1))) continue;

            container.set(x & 3, y & 3, z & 3, change.biome());
            changed = true;
        }

        if (changed) {
            chunk.markUnsaved();

            List<ServerPlayer> players = world.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
            if (!players.isEmpty()) {
                var packet = ClientboundChunksBiomesPacket.forChunks(List.of(chunk));
                for (ServerPlayer serverPlayer : players) {
                    serverPlayer.connection.send(packet);
                }
            }
        }
    }

}
