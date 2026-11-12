package com.moulberry.axiom;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WorldExtension {

    private static final Map<ResourceKey<Level>, WorldExtension> extensions = new ConcurrentHashMap<>();

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

    public static class FoliaMarkerTracker implements Listener {
        @EventHandler
        public void onEntityAdd(EntityAddToWorldEvent event) {
            if (event.getEntity() instanceof org.bukkit.entity.Marker marker) {
                get(((CraftWorld) event.getWorld()).getHandle()).trackedMarkers.add(marker);
            }
        }

        @EventHandler
        public void onEntityRemove(EntityRemoveFromWorldEvent event) {
            if (event.getEntity() instanceof org.bukkit.entity.Marker marker) {
                get(((CraftWorld) event.getWorld()).getHandle()).trackedMarkers.remove(marker);
            }
        }
    }

    private volatile ServerLevel level;

    private final LongSet pendingChunksToSend = new LongOpenHashSet();
    private final LongSet pendingChunksToLight = new LongOpenHashSet();
    private final Map<UUID, MarkerData> previousMarkerData = new ConcurrentHashMap<>();

    private final Set<org.bukkit.entity.Marker> trackedMarkers = ConcurrentHashMap.newKeySet();
    private final Queue<MarkerData> changedMarkerQueue = new ConcurrentLinkedQueue<>();

    public void sendChunk(int cx, int cz) {
        synchronized (this.pendingChunksToSend) {
            this.pendingChunksToSend.add(ChunkPos.asLong(cx, cz));
        }
    }

    public void lightChunk(int cx, int cz) {
        synchronized (this.pendingChunksToLight) {
            this.pendingChunksToLight.add(ChunkPos.asLong(cx, cz));
        }
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
            if (FoliaSupport.FOLIA) {
                this.tickMarkersFolia();
            } else {
                this.tickMarkers();
            }
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

        this.sendMarkerChanges(changedData, missingUuids);
    }

    private void tickMarkersFolia() {
        List<MarkerData> changedData = new ArrayList<>();
        MarkerData queued;
        while ((queued = this.changedMarkerQueue.poll()) != null) {
            changedData.add(queued);
        }

        Set<UUID> allMarkers = new HashSet<>();

        for (org.bukkit.entity.Marker bukkitMarker : this.trackedMarkers) {
            if (ImplAxiomHiddenEntities.isMarkerHidden(bukkitMarker)) {
                continue;
            }

            UUID uuid = bukkitMarker.getUniqueId();
            allMarkers.add(uuid);

            boolean scheduled = bukkitMarker.getScheduler().execute(AxiomPaper.PLUGIN, () -> {
                if (!this.trackedMarkers.contains(bukkitMarker)) {
                    return;
                }

                Marker marker = (Marker) ((CraftEntity) bukkitMarker).getHandle();
                MarkerData currentData = MarkerData.createFrom(marker);

                MarkerData previousData = this.previousMarkerData.put(uuid, currentData);
                if (!Objects.equals(currentData, previousData)) {
                    this.changedMarkerQueue.add(currentData);
                }
            }, () -> this.trackedMarkers.remove(bukkitMarker), 1L);

            if (!scheduled) {
                this.trackedMarkers.remove(bukkitMarker);
            }
        }

        Set<UUID> missingUuids = new HashSet<>(this.previousMarkerData.keySet());
        missingUuids.removeAll(allMarkers);
        this.previousMarkerData.keySet().removeAll(missingUuids);

        this.sendMarkerChanges(changedData, missingUuids);
    }

    private void sendMarkerChanges(List<MarkerData> changedData, Set<UUID> missingUuids) {
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

    private static long[] drain(LongSet set, int max) {
        synchronized (set) {
            if (set.isEmpty()) {
                return new long[0];
            }

            if (max <= 0 || set.size() <= max) {
                long[] all = set.toLongArray();
                set.clear();
                return all;
            }

            long[] some = new long[max];
            LongIterator iterator = set.longIterator();
            for (int i = 0; i < max; i++) {
                some[i] = iterator.nextLong();
                iterator.remove();
            }
            return some;
        }
    }

    private void tickChunkRelight(int maxChunkRelightsPerTick, int maxChunkSendsPerTick) {
        ServerLevel level = this.level;

        for (long chunkPosLong : drain(this.pendingChunksToSend, maxChunkSendsPerTick)) {
            int chunkX = ChunkPos.getX(chunkPosLong);
            int chunkZ = ChunkPos.getZ(chunkPosLong);
            FoliaSupport.runAtChunk(level, chunkX, chunkZ, () -> {
                try {
                    sendChunkNow(level, chunkX, chunkZ);
                } catch (Throwable t) {
                    AxiomPaper.PLUGIN.getLogger().log(java.util.logging.Level.WARNING, "Failed to resend chunk " + chunkX + "," + chunkZ + " after an edit", t);
                }
            });
        }

        long[] toLight = drain(this.pendingChunksToLight, maxChunkRelightsPerTick);
        if (toLight.length > 0) {
            Set<ChunkPos> chunkSet = new HashSet<>();
            for (long chunkPosLong : toLight) {
                chunkSet.add(new ChunkPos(chunkPosLong));
            }

            level.getChunkSource().getLightEngine().starlight$serverRelightChunks(chunkSet, pos -> {}, count -> {});
        }
    }

    private static void sendChunkNow(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }

        List<ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(chunkX, chunkZ), false);
        if (players.isEmpty()) {
            return;
        }

        var packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null, false);
        for (ServerPlayer player : players) {
            player.connection.send(packet);
        }
    }

}
