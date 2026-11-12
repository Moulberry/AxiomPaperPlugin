package com.moulberry.axiom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class FoliaSupport {

    public static final boolean FOLIA = detectFolia();

    private FoliaSupport() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isOwned(ServerLevel level, int chunkX, int chunkZ) {
        return !FOLIA || Bukkit.isOwnedByCurrentRegion(level.getWorld(), chunkX, chunkZ);
    }

    public static boolean isOwned(ServerLevel level, BlockPos blockPos) {
        return isOwned(level, blockPos.getX() >> 4, blockPos.getZ() >> 4);
    }

    public static boolean isOwned(Entity entity) {
        return !FOLIA || Bukkit.isOwnedByCurrentRegion(entity);
    }

    public static void runAtChunk(ServerLevel level, int chunkX, int chunkZ, Runnable task) {
        if (isOwned(level, chunkX, chunkZ)) {
            task.run();
        } else {
            Bukkit.getRegionScheduler().execute(AxiomPaper.PLUGIN, level.getWorld(), chunkX, chunkZ, task);
        }
    }

    public static void runForEntity(Entity entity, Runnable task, @Nullable Runnable retired) {
        if (!FOLIA || Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
        } else if (!entity.getScheduler().execute(AxiomPaper.PLUGIN, task, retired, 1L)) {
            if (retired != null) {
                retired.run();
            }
        }
    }

    public static void runForEntity(Entity entity, Runnable task) {
        runForEntity(entity, task, null);
    }

    public static void runGlobal(Runnable task) {
        if (!FOLIA || Bukkit.isGlobalTickThread()) {
            task.run();
        } else {
            Bukkit.getGlobalRegionScheduler().execute(AxiomPaper.PLUGIN, task);
        }
    }

    public static void runDeferred(MinecraftServer server, Runnable task) {
        if (FOLIA) {
            task.run();
        } else {
            server.execute(task);
        }
    }

}
