package com.moulberry.axiom.integration.prism;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.annotations.ServerAnnotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.prism_mc.prism.api.activities.Activity;
import org.prism_mc.prism.api.services.modifications.ModificationHandler;
import org.prism_mc.prism.api.services.modifications.ModificationQueueMode;
import org.prism_mc.prism.api.services.modifications.ModificationResult;
import org.prism_mc.prism.api.services.modifications.ModificationRuleset;

import java.util.UUID;

final class PrismAxiomHandlers {
    private PrismAxiomHandlers() {
    }

    static final class EntityCreateHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntitySnapshot action = (PrismAxiomActions.EntitySnapshot) activity.action();
            return applyEntitySnapshot(activity, mode, action.entityUuid(), null);
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntitySnapshot action = (PrismAxiomActions.EntitySnapshot) activity.action();
            return applyEntitySnapshot(activity, mode, action.entityUuid(), action.nextState());
        }
    }

    static final class EntityDeleteHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntitySnapshot action = (PrismAxiomActions.EntitySnapshot) activity.action();
            return applyEntitySnapshot(activity, mode, action.entityUuid(), action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntitySnapshot action = (PrismAxiomActions.EntitySnapshot) activity.action();
            return applyEntitySnapshot(activity, mode, action.entityUuid(), null);
        }
    }

    static final class EntityModifyHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntitySnapshot action = (PrismAxiomActions.EntitySnapshot) activity.action();
            return applyEntitySnapshot(activity, mode, action.entityUuid(), action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntitySnapshot action = (PrismAxiomActions.EntitySnapshot) activity.action();
            return applyEntitySnapshot(activity, mode, action.entityUuid(), action.nextState());
        }
    }

    static final class PlayerTeleportHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerTeleport(activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerTeleport(activity, mode, action.nextState());
        }
    }

    static final class PlayerGamemodeHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerGamemode(activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerGamemode(activity, mode, action.nextState());
        }
    }

    static final class PlayerFlySpeedHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerFlySpeed(activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerFlySpeed(activity, mode, action.nextState());
        }
    }

    static final class PlayerNoPhysicalTriggerHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerNoPhysicalTrigger(activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerNoPhysicalTrigger(activity, mode, action.nextState());
        }
    }

    static final class WorldTimeHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.GenericState action = (PrismAxiomActions.GenericState) activity.action();
            return applyWorldTime(activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.GenericState action = (PrismAxiomActions.GenericState) activity.action();
            return applyWorldTime(activity, mode, action.nextState());
        }
    }

    static final class WorldPropertyHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.GenericState action = (PrismAxiomActions.GenericState) activity.action();
            return applyWorldProperty(activity, mode, action.descriptor(), action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.GenericState action = (PrismAxiomActions.GenericState) activity.action();
            return applyWorldProperty(activity, mode, action.descriptor(), action.nextState());
        }
    }

    static final class AnnotationSnapshotHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.GenericState action = (PrismAxiomActions.GenericState) activity.action();
            return applyAnnotationSnapshot(activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.GenericState action = (PrismAxiomActions.GenericState) activity.action();
            return applyAnnotationSnapshot(activity, mode, action.nextState());
        }
    }

    private static ModificationResult applyEntitySnapshot(
        Activity activity,
        ModificationQueueMode mode,
        @Nullable UUID entityUuid,
        @Nullable String entitySnapshot
    ) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        ServerLevel level = PrismAxiomContext.serverLevel(activity);
        if (level == null || entityUuid == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        Entity existingEntity = level.getEntity(entityUuid);
        if (existingEntity != null) {
            discardEntity(existingEntity);
        }

        if (entitySnapshot == null || entitySnapshot.isEmpty()) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        try {
            CompoundTag entityTag = TagParser.parseCompoundFully(entitySnapshot);
            Entity restoredEntity = net.minecraft.world.entity.EntityType.loadEntityRecursive(
                entityTag,
                level,
                EntitySpawnReason.COMMAND,
                loadedEntity -> loadedEntity
            );
            if (restoredEntity != null) {
                level.tryAddFreshEntityWithPassengers(restoredEntity);
            }
            return PrismAxiomContext.defaultResult(activity, mode);
        } catch (Exception exception) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to restore entity snapshot for Prism: " + exception.getMessage());
            return PrismAxiomContext.erroredResult(activity);
        }
    }

    private static void discardEntity(Entity entity) {
        for (Entity passenger : entity.getIndirectPassengers()) {
            passenger.discard();
        }
        entity.discard();
    }

    private static ModificationResult applyPlayerTeleport(Activity activity, ModificationQueueMode mode, String encodedLocation) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
        Player player = PrismAxiomContext.onlinePlayer(action.playerContainer());
        if (player == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        player.teleport(PrismAxiomSerialization.decodeLocation(encodedLocation));
        return PrismAxiomContext.defaultResult(activity, mode);
    }

    private static ModificationResult applyPlayerGamemode(Activity activity, ModificationQueueMode mode, String encodedGamemode) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
        Player player = PrismAxiomContext.onlinePlayer(action.playerContainer());
        if (player == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        player.setGameMode(GameMode.valueOf(encodedGamemode));
        return PrismAxiomContext.defaultResult(activity, mode);
    }

    private static ModificationResult applyPlayerFlySpeed(Activity activity, ModificationQueueMode mode, String encodedFlySpeed) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
        Player player = PrismAxiomContext.onlinePlayer(action.playerContainer());
        if (player == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        ((CraftPlayer) player).getHandle().getAbilities().setFlyingSpeed(Float.parseFloat(encodedFlySpeed));
        return PrismAxiomContext.defaultResult(activity, mode);
    }

    private static ModificationResult applyPlayerNoPhysicalTrigger(Activity activity, ModificationQueueMode mode, String encodedValue) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
        AxiomPaper.PLUGIN.setNoPhysicalTrigger(action.playerContainer().uuid(), Boolean.parseBoolean(encodedValue));
        return PrismAxiomContext.defaultResult(activity, mode);
    }

    private static ModificationResult applyWorldTime(Activity activity, ModificationQueueMode mode, String encodedState) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        World world = PrismAxiomContext.world(activity);
        if (world == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        PrismAxiomSerialization.WorldTimeState worldTimeState = PrismAxiomSerialization.decodeWorldTimeState(encodedState);
        world.setTime(worldTimeState.time());
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, worldTimeState.daylightCycleEnabled());
        return PrismAxiomContext.defaultResult(activity, mode);
    }

    private static ModificationResult applyWorldProperty(
        Activity activity,
        ModificationQueueMode mode,
        String propertyId,
        String encodedValue
    ) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        World world = PrismAxiomContext.world(activity);
        if (world == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        var worldPropertyRegistry = AxiomPaper.PLUGIN.getWorldPropertiesIfPresent(world);
        if (worldPropertyRegistry == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        var worldPropertyHolder = worldPropertyRegistry.getById(VersionHelper.createIdentifier(propertyId));
        if (worldPropertyHolder == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        worldPropertyHolder.setSerializedValue(world, PrismAxiomSerialization.decodeBytes(encodedValue));
        return PrismAxiomContext.defaultResult(activity, mode);
    }

    private static ModificationResult applyAnnotationSnapshot(Activity activity, ModificationQueueMode mode, String encodedSnapshot) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        World world = PrismAxiomContext.world(activity);
        if (world == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        ServerAnnotations.applySnapshot(world, PrismAxiomSerialization.decodeBytes(encodedSnapshot));
        return PrismAxiomContext.defaultResult(activity, mode);
    }
}
