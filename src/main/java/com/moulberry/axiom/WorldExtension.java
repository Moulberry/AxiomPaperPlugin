package com.moulberry.axiom;

import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

public class WorldExtension {

    private static final Map<ResourceKey<Level>, WorldExtension> extensions = new HashMap<>();

    public static WorldExtension get(ServerLevel serverLevel) {
        WorldExtension extension = extensions.computeIfAbsent(serverLevel.dimension(), k -> new WorldExtension());
        extension.level = serverLevel;
        return extension;
    }

    public static void tick(MinecraftServer server, int maxChunkRelightsPerTick, int maxChunkSendsPerTick) {
        extensions.keySet().retainAll(server.levelKeys());

        for (ServerLevel level : server.getAllLevels()) {
            WorldExtension extension = extensions.get(level.dimension());
            if (extension != null) {
                extension.level = level;
                extension.tick(maxChunkRelightsPerTick, maxChunkSendsPerTick);
            }
        }
    }

    private ServerLevel level;

    private final LongSet pendingChunksToSend = new LongOpenHashSet();
    private final LongSet pendingChunksToLight = new LongOpenHashSet();

    public void sendChunk(int cx, int cz) {
        this.pendingChunksToSend.add(ChunkPos.asLong(cx, cz));
    }

    public void lightChunk(int cx, int cz) {
        this.pendingChunksToLight.add(ChunkPos.asLong(cx, cz));
    }

    public void tick(int maxChunkRelightsPerTick, int maxChunkSendsPerTick) {
        ChunkMap chunkMap = this.level.getChunkSource().chunkMap;

        boolean sendAll = maxChunkSendsPerTick <= 0;

        // Send chunks
        LongIterator longIterator = this.pendingChunksToSend.longIterator();
        while (longIterator.hasNext()) {
            ChunkPos chunkPos = new ChunkPos(longIterator.nextLong());
            List<ServerPlayer> players = chunkMap.getPlayers(chunkPos, false);
            if (players.isEmpty()) continue;

            LevelChunk chunk = this.level.getChunk(chunkPos.x, chunkPos.z);
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

        this.level.getChunkSource().getLightEngine().relight(chunkSet, pos -> {}, count -> {});
    }

}
