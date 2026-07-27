package com.moulberry.axiom.integration.prism;

import com.moulberry.axiom.AxiomPaper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.prism_mc.prism.api.activities.Activity;
import org.prism_mc.prism.api.containers.PlayerContainer;
import org.prism_mc.prism.api.services.modifications.ModificationQueueMode;
import org.prism_mc.prism.api.services.modifications.ModificationResult;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Set;

final class PrismAxiomContext {
    private static final Set<LevelChunk> PENDING_BIOME_UPDATES = new HashSet<>();
    private static boolean biomeUpdateScheduled;

    private PrismAxiomContext() {
    }

    static ModificationResult defaultResult(Activity activity, ModificationQueueMode mode) {
        return ModificationResult.builder().activity(activity).statusFromMode(mode).build();
    }

    static ModificationResult skippedResult(Activity activity) {
        return ModificationResult.builder().activity(activity).skipped().build();
    }

    static ModificationResult erroredResult(Activity activity) {
        return ModificationResult.builder().activity(activity).errored().build();
    }

    @Nullable
    static Player onlinePlayer(PlayerContainer playerContainer) {
        return playerContainer.uuid() == null ? null : Bukkit.getPlayer(playerContainer.uuid());
    }

    @Nullable
    static Player actor(Object owner) {
        if (owner instanceof Player player) {
            return player;
        }
        return null;
    }

    @Nullable
    static World world(Activity activity) {
        return Bukkit.getWorld(activity.worldUuid());
    }

    @Nullable
    static ServerLevel serverLevel(Activity activity) {
        World world = world(activity);
        return world instanceof CraftWorld craftWorld ? craftWorld.getHandle() : null;
    }

    static void queueBiomeUpdate(LevelChunk chunk) {
        PENDING_BIOME_UPDATES.add(chunk);
        if (biomeUpdateScheduled) {
            return;
        }

        biomeUpdateScheduled = true;
        try {
            Bukkit.getScheduler().runTask(AxiomPaper.PLUGIN, PrismAxiomContext::flushBiomeUpdates);
        } catch (RuntimeException exception) {
            clearPendingBiomeUpdates();
            AxiomPaper.PLUGIN.getLogger().warning(
                "Failed to schedule Prism biome updates: " + exception.getMessage()
            );
        }
    }

    static void clearPendingBiomeUpdates() {
        PENDING_BIOME_UPDATES.clear();
        biomeUpdateScheduled = false;
    }

    private static void flushBiomeUpdates() {
        Set<LevelChunk> chunks = Set.copyOf(PENDING_BIOME_UPDATES);
        PENDING_BIOME_UPDATES.clear();
        biomeUpdateScheduled = false;

        for (LevelChunk chunk : chunks) {
            try {
                ServerLevel level = (ServerLevel) chunk.getLevel();
                var recipients = level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
                if (recipients.isEmpty()) {
                    continue;
                }

                var packet = ClientboundChunksBiomesPacket.forChunks(java.util.List.of(chunk));
                for (var recipient : recipients) {
                    try {
                        recipient.connection.send(packet);
                    } catch (RuntimeException exception) {
                        AxiomPaper.PLUGIN.getLogger().warning(
                            "Failed to send a Prism biome update to " + recipient.getScoreboardName()
                                + ": " + exception.getMessage()
                        );
                    }
                }
            } catch (RuntimeException exception) {
                AxiomPaper.PLUGIN.getLogger().warning(
                    "Failed to flush a Prism biome update for chunk " + chunk.getPos()
                        + ": " + exception.getMessage()
                );
            }
        }
    }
}
