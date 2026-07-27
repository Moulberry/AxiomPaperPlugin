package com.moulberry.axiom.integration.prism;

import com.moulberry.axiom.AxiomPaper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class PrismIntegration {
    private static boolean available;

    private PrismIntegration() {
    }

    public static boolean initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("prism")) {
            return false;
        }

        try {
            available = PrismIntegrationImpl.isEnabled();
            if (available) {
                PrismAxiomIntegration.initialize();
            }
        } catch (RuntimeException | LinkageError exception) {
            AxiomPaper.PLUGIN.getLogger().log(
                java.util.logging.Level.WARNING,
                "Failed to initialize Prism integration",
                exception
            );
            available = false;
        }
        return available;
    }

    public static boolean isEnabled() {
        return isEnabled(PrismLoggingType.BLOCK_CHANGES);
    }

    public static void shutdown() {
        if (!available) {
            return;
        }
        try {
            PrismAxiomContext.clearPendingBiomeUpdates();
        } catch (RuntimeException | LinkageError exception) {
            AxiomPaper.PLUGIN.getLogger().log(
                java.util.logging.Level.WARNING,
                "Failed to shut down Prism integration",
                exception
            );
        } finally {
            available = false;
        }
    }

    private static boolean isEnabled(PrismLoggingType loggingType) {
        return available && AxiomPaper.PLUGIN.shouldLogPrism(loggingType);
    }

    public static void logChange(Player player, BlockState oldBlockState, @Nullable String oldBlockEntityNbt,
                                 BlockState newBlockState, @Nullable String newBlockEntityNbt, CraftWorld world, BlockPos pos) {
        if (!isEnabled()) {
            return;
        }

        PrismIntegrationImpl.logChange(player, oldBlockState, oldBlockEntityNbt, newBlockState, newBlockEntityNbt, world, pos);
    }

    public static void logEntitySpawn(Player actor, Entity entity) {
        if (isEnabled(PrismLoggingType.ENTITY_SPAWNS)) {
            PrismAxiomIntegration.logEntitySpawn(actor, entity);
        }
    }

    public static void logEntityDelete(Player actor, Entity entity) {
        if (isEnabled(PrismLoggingType.ENTITY_DELETES)) {
            PrismAxiomIntegration.logEntityDelete(actor, entity);
        }
    }

    public static void logEntityModification(
        Player actor,
        Entity entity,
        String previousSnapshot,
        String nextSnapshot
    ) {
        if (isEnabled(PrismLoggingType.ENTITY_MODIFICATIONS)) {
            PrismAxiomIntegration.logEntityModification(actor, entity, previousSnapshot, nextSnapshot);
        }
    }

    @Nullable
    public static String captureEntitySnapshot(Entity entity) {
        if (!isEnabled(PrismLoggingType.ENTITY_MODIFICATIONS)) {
            return null;
        }
        return PrismAxiomIntegration.captureEntitySnapshot(entity);
    }

    @Nullable
    public static String captureEntityState(Entity entity) {
        if (!isEnabled(PrismLoggingType.ENTITY_MODIFICATIONS)) {
            return null;
        }
        return PrismAxiomIntegration.captureEntityState(entity);
    }

    public static void logEntityPassengers(Player actor, Entity entity, String previous, String next) {
        if (isEnabled(PrismLoggingType.ENTITY_MODIFICATIONS)) {
            PrismAxiomIntegration.logEntityPassengers(actor, entity, previous, next);
        }
    }

    public static void logPlayerTeleport(Player actor, Player target, Location previous, Location next) {
        if (isEnabled(PrismLoggingType.PLAYER_TELEPORTS)) {
            PrismAxiomIntegration.logPlayerTeleport(actor, target, previous, next);
        }
    }

    public static void logPlayerGamemode(Player actor, Player target, GameMode previous, GameMode next) {
        if (isEnabled(PrismLoggingType.PLAYER_GAMEMODE_CHANGES)) {
            PrismAxiomIntegration.logPlayerGamemode(actor, target, previous, next);
        }
    }

    public static void logPlayerFlySpeed(Player actor, Player target, float previous, float next) {
        if (isEnabled(PrismLoggingType.PLAYER_FLY_SPEED_CHANGES)) {
            PrismAxiomIntegration.logPlayerFlySpeed(actor, target, previous, next);
        }
    }

    public static void logPlayerNoPhysicalTrigger(Player actor, Player target, boolean previous, boolean next) {
        if (isEnabled(PrismLoggingType.PLAYER_NO_PHYSICAL_TRIGGER_CHANGES)) {
            PrismAxiomIntegration.logPlayerNoPhysicalTrigger(actor, target, previous, next);
        }
    }

    public static void logWorldTimeChange(
        Player actor,
        World world,
        long previousTime,
        boolean previousDaylightCycleEnabled,
        long nextTime,
        boolean nextDaylightCycleEnabled
    ) {
        if (isEnabled(PrismLoggingType.WORLD_TIME_CHANGES)) {
            PrismAxiomIntegration.logWorldTimeChange(
                actor,
                world,
                previousTime,
                previousDaylightCycleEnabled,
                nextTime,
                nextDaylightCycleEnabled
            );
        }
    }

    public static void logWorldPropertyChange(
        Player actor,
        World world,
        String propertyId,
        byte[] previous,
        byte[] next
    ) {
        if (isEnabled(PrismLoggingType.WORLD_PROPERTY_CHANGES)) {
            PrismAxiomIntegration.logWorldPropertyChange(actor, world, propertyId, previous, next);
        }
    }

    public static void logAnnotationChange(Player actor, World world, byte[] rollbackActions, byte[] restoreActions) {
        if (isEnabled(PrismLoggingType.ANNOTATION_CHANGES)) {
            PrismAxiomIntegration.logAnnotationChange(actor, world, rollbackActions, restoreActions);
        }
    }

    public static void logBiomeChange(
        Player actor,
        World world,
        int quartX,
        int quartY,
        int quartZ,
        String previousBiome,
        String nextBiome
    ) {
        if (isEnabled(PrismLoggingType.BIOME_CHANGES)) {
            PrismAxiomIntegration.logBiomeChange(
                actor,
                world,
                quartX,
                quartY,
                quartZ,
                previousBiome,
                nextBiome
            );
        }
    }
}
