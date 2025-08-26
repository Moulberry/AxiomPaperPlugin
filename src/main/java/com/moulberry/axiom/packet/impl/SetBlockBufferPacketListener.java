package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
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

    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        ServerPlayer serverPlayer = ((CraftPlayer)player).getHandle();
        MinecraftServer server = serverPlayer.getServer();
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
        server.execute(() -> {
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
                    return;
                }

                ServerLevel world = player.serverLevel();
                if (!world.dimension().equals(worldKey) || !this.plugin.canModifyWorld(player.getBukkitEntity(), world.getWorld())) {
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
        server.execute(() -> {
            try {
                if (this.plugin.logLargeBlockBufferChanges()) {
                    this.plugin.getLogger().info("Player " + player.getUUID() + " modified " + biomeBuffer.getSectionCount() + " chunk sections (biomes)");
                }

                if (!this.plugin.consumeDispatchSends(player.getBukkitEntity(), biomeBuffer.getSectionCount(), clientAvailableDispatchSends)) {
                    return;
                }

                if (!this.plugin.canUseAxiom(player.getBukkitEntity(), AxiomPermission.BUILD_SECTION)) {
                    return;
                }

                ServerLevel world = player.serverLevel();
                if (!world.dimension().equals(worldKey) || !this.plugin.canModifyWorld(player.getBukkitEntity(), world.getWorld())) {
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
                        LevelChunk chunk = (LevelChunk) world.getChunk(x >> 2, z >> 2, ChunkStatus.FULL, false);
                        if (chunk == null) return;

                        var section = chunk.getSection(cy - minSection);
                        PalettedContainer<Holder<Biome>> container = (PalettedContainer<Holder<Biome>>) section.getBiomes();

                        if (!Integration.canPlaceBlock(player.getBukkitEntity(),
                            new Location(player.getBukkitEntity().getWorld(), (x<<2)+1, (y<<2)+1, (z<<2)+1))) return;

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
