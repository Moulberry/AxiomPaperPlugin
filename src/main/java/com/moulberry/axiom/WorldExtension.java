package com.moulberry.axiom;

import com.moulberry.axiom.annotations.ServerAnnotations;
import com.moulberry.axiom.marker.MarkerData;
import com.moulberry.axiom.paperapi.entity.ImplAxiomHiddenEntities;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.*;

public class WorldExtension {

    private static final Map<ResourceKey<Level>, WorldExtension> extensions = new HashMap<>();

    public static WorldExtension get(ServerLevel serverLevel) {
        WorldExtension extension = extensions.computeIfAbsent(serverLevel.dimension(), k -> new WorldExtension());
        extension.level = serverLevel;
        return extension;
    }

    public static void onPlayerJoin(World world, Player player) {
        ServerLevel level = ((CraftWorld)world).getHandle();
        get(level).onPlayerJoin(player);

        if (AxiomPaper.PLUGIN.canUseAxiom(player)) {
            ServerAnnotations.sendAll(world, ((CraftPlayer)player).getHandle());
        }
    }

    public static void tick(MinecraftServer server, boolean sendMarkers, int maxChunkRelightsPerTick, int maxChunkSendsPerTick) {
        extensions.keySet().retainAll(server.levelKeys());

        for (ServerLevel level : server.getAllLevels()) {
            get(level).tick(sendMarkers, maxChunkRelightsPerTick, maxChunkSendsPerTick);
        }
    }

    private ServerLevel level;

    private final LongSet pendingChunksToSend = new LongOpenHashSet();
    private final LongSet pendingChunksToLight = new LongOpenHashSet();
    private final Map<UUID, MarkerData> previousMarkerData = new HashMap<>();

    public void sendChunk(int cx, int cz) {
        this.pendingChunksToSend.add(ChunkPos.asLong(cx, cz));
    }

    public void lightChunk(int cx, int cz) {
        this.pendingChunksToLight.add(ChunkPos.asLong(cx, cz));
    }

    public void onPlayerJoin(Player player) {
        if (!this.previousMarkerData.isEmpty()) {
            List<MarkerData> markerData = new ArrayList<>(this.previousMarkerData.values());

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeCollection(markerData, MarkerData::write);
            buf.writeCollection(Set.<UUID>of(), (buffer, uuid) -> buffer.writeUUID(uuid));

            byte[] bytes = ByteBufUtil.getBytes(buf);
            VersionHelper.sendCustomPayload(player, "axiom:marker_data", bytes);
        }

        try {
            ServerPlayer serverPlayer = ((CraftPlayer)player).getHandle();
            if (this.level.chunkPacketBlockController.shouldModify(serverPlayer, this.level.getChunkIfLoaded(serverPlayer.blockPosition()))) {
                Component text = Component.text("Axiom: Warning, anti-xray is enabled. This will cause issues when copying blocks. Please turn anti-xray off");
                player.sendMessage(text.color(NamedTextColor.RED));
            }
        } catch (Throwable ignored) {}
    }

    public void tick(boolean sendMarkers, int maxChunkRelightsPerTick, int maxChunkSendsPerTick) {
        if (sendMarkers) {
            this.tickMarkers();
        }
        this.tickChunkRelight(maxChunkRelightsPerTick, maxChunkSendsPerTick);
    }

    private void tickMarkers() {
        List<MarkerData> changedData = new ArrayList<>();

        Set<UUID> allMarkers = new HashSet<>();

        for (Entity entity : this.level.getEntities().getAll()) {
            if (entity instanceof Marker marker) {
                if (ImplAxiomHiddenEntities.isMarkerHidden((org.bukkit.entity.Marker) marker.getBukkitEntity())) {
                    continue;
                }

                MarkerData currentData = MarkerData.createFrom(marker);

                MarkerData previousData = this.previousMarkerData.get(marker.getUUID());
                if (!Objects.equals(currentData, previousData)) {
                    this.previousMarkerData.put(marker.getUUID(), currentData);
                    changedData.add(currentData);
                }

                allMarkers.add(marker.getUUID());
            }
        }

        Set<UUID> missingUuids = new HashSet<>(this.previousMarkerData.keySet());
        missingUuids.removeAll(allMarkers);
        this.previousMarkerData.keySet().removeAll(missingUuids);

        if (!changedData.isEmpty() || !missingUuids.isEmpty()) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeCollection(changedData, MarkerData::write);
            buf.writeCollection(missingUuids, (buffer, uuid) -> buffer.writeUUID(uuid));
            byte[] bytes = ByteBufUtil.getBytes(buf);

            List<ServerPlayer> players = new ArrayList<>();

            for (ServerPlayer player : this.level.players()) {
                if (AxiomPaper.PLUGIN.canUseAxiom(player.getBukkitEntity())) {
                    players.add(player);
                }
            }

            VersionHelper.sendCustomPayloadToAll(players, "axiom:marker_data", bytes);
        }
    }

    private void tickChunkRelight(int maxChunkRelightsPerTick, int maxChunkSendsPerTick) {
        ChunkMap chunkMap = this.level.getChunkSource().chunkMap;

        boolean sendAll = maxChunkSendsPerTick <= 0;

        // Send chunks
        LongIterator longIterator = this.pendingChunksToSend.longIterator();
        while (longIterator.hasNext()) {
            ChunkPos chunkPos = new ChunkPos(longIterator.nextLong());

            LevelChunk chunk = this.level.getChunkIfLoaded(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                continue;
            }

            List<ServerPlayer> players = chunkMap.getPlayers(chunkPos, false);
            if (players.isEmpty()) {
                continue;
            }

            var packet = new ClientboundLevelChunkWithLightPacket(chunk, this.level.getLightEngine(), null, null, false);
            for (ServerPlayer player : players) {
                player.connection.send(packet);
            }

            if (!sendAll) {
                longIterator.remove();

                maxChunkSendsPerTick -= 1;
                if (maxChunkSendsPerTick <= 0) {
                    break;
                }
            }
        }
        if (sendAll) {
            this.pendingChunksToSend.clear();
        }

        // Relight chunks
        Set<ChunkPos> chunkSet = new HashSet<>();
        longIterator = this.pendingChunksToLight.longIterator();
        if (maxChunkRelightsPerTick <= 0) {
            while (longIterator.hasNext()) {
                chunkSet.add(new ChunkPos(longIterator.nextLong()));
            }
            this.pendingChunksToLight.clear();
        } else {
            while (longIterator.hasNext()) {
                chunkSet.add(new ChunkPos(longIterator.nextLong()));
                longIterator.remove();

                maxChunkRelightsPerTick -= 1;
                if (maxChunkRelightsPerTick <= 0) {
                    break;
                }
            }
        }

        this.level.getChunkSource().getLightEngine().starlight$serverRelightChunks(chunkSet, pos -> {}, count -> {});
    }

}
