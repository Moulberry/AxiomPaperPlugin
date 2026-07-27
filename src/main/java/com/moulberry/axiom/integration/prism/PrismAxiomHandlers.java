package com.moulberry.axiom.integration.prism;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.annotations.ServerAnnotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.ProblemReporter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
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
import org.prism_mc.prism.api.services.modifications.ModificationSkipReason;
import org.prism_mc.prism.paper.api.containers.PaperEntityContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PrismAxiomHandlers {
    private PrismAxiomHandlers() {
    }

    static ModificationHandler safe(ModificationHandler delegate) {
        return new ModificationHandler() {
            @Override
            public ModificationResult applyRollback(
                ModificationRuleset modificationRuleset,
                Object owner,
                Activity activity,
                ModificationQueueMode mode
            ) {
                try {
                    return delegate.applyRollback(modificationRuleset, owner, activity, mode);
                } catch (RuntimeException exception) {
                    logModificationFailure(activity, "rollback", exception);
                    return PrismAxiomContext.erroredResult(activity);
                }
            }

            @Override
            public ModificationResult applyRestore(
                ModificationRuleset modificationRuleset,
                Object owner,
                Activity activity,
                ModificationQueueMode mode
            ) {
                try {
                    return delegate.applyRestore(modificationRuleset, owner, activity, mode);
                } catch (RuntimeException exception) {
                    logModificationFailure(activity, "restore", exception);
                    return PrismAxiomContext.erroredResult(activity);
                }
            }
        };
    }

    private static void logModificationFailure(Activity activity, String operation, RuntimeException exception) {
        AxiomPaper.PLUGIN.getLogger().warning(
            "Failed to " + operation + " Prism action " + activity.action().type().key() + ": " + exception.getMessage()
        );
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
            return applyEntitySnapshot(modificationRuleset, activity, mode, action, null);
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntitySnapshot action = (PrismAxiomActions.EntitySnapshot) activity.action();
            return applyEntitySnapshot(modificationRuleset, activity, mode, action, action.nextState());
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
            return restoreDeletedEntity(modificationRuleset, activity, mode, action);
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntitySnapshot action = (PrismAxiomActions.EntitySnapshot) activity.action();
            return removeDeletedEntity(modificationRuleset, activity, mode, action);
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
            return applyEntityState(
                modificationRuleset, activity, mode, action, action.previousState()
            );
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntitySnapshot action = (PrismAxiomActions.EntitySnapshot) activity.action();
            return applyEntityState(
                modificationRuleset, activity, mode, action, action.nextState()
            );
        }
    }

    static final class EntityPassengersHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntityPassengers action = (PrismAxiomActions.EntityPassengers) activity.action();
            return applyEntityPassengers(modificationRuleset, activity, mode, action, action.previousPassengers());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.EntityPassengers action = (PrismAxiomActions.EntityPassengers) activity.action();
            return applyEntityPassengers(modificationRuleset, activity, mode, action, action.nextPassengers());
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
            return applyPlayerTeleport(modificationRuleset, activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerTeleport(modificationRuleset, activity, mode, action.nextState());
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
            return applyPlayerGamemode(modificationRuleset, activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerGamemode(modificationRuleset, activity, mode, action.nextState());
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
            return applyPlayerFlySpeed(modificationRuleset, activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerFlySpeed(modificationRuleset, activity, mode, action.nextState());
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
            return applyPlayerNoPhysicalTrigger(modificationRuleset, activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
            return applyPlayerNoPhysicalTrigger(modificationRuleset, activity, mode, action.nextState());
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
            return applyWorldTime(modificationRuleset, activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.GenericState action = (PrismAxiomActions.GenericState) activity.action();
            return applyWorldTime(modificationRuleset, activity, mode, action.nextState());
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
            return applyWorldProperty(
                modificationRuleset, owner, activity, mode, action.descriptor(), action.previousState()
            );
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.GenericState action = (PrismAxiomActions.GenericState) activity.action();
            return applyWorldProperty(
                modificationRuleset, owner, activity, mode, action.descriptor(), action.nextState()
            );
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
            return applyAnnotationSnapshot(modificationRuleset, activity, mode, action.previousState());
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.GenericState action = (PrismAxiomActions.GenericState) activity.action();
            return applyAnnotationSnapshot(modificationRuleset, activity, mode, action.nextState());
        }
    }

    static final class BiomeStateHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.BiomeState action = (PrismAxiomActions.BiomeState) activity.action();
            return applyBiome(
                modificationRuleset, activity, mode, action.previousBiome()
            );
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            PrismAxiomActions.BiomeState action = (PrismAxiomActions.BiomeState) activity.action();
            return applyBiome(
                modificationRuleset, activity, mode, action.nextBiome()
            );
        }
    }

    private static ModificationResult applyEntitySnapshot(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        PrismAxiomActions.EntitySnapshot action,
        @Nullable String entitySnapshot
    ) {
        String rootBlacklistTarget = blacklistedActionEntityTarget(modificationRuleset, action);
        if (rootBlacklistTarget != null) {
            return blacklistedResult(activity, rootBlacklistTarget);
        }

        ServerLevel level = PrismAxiomContext.serverLevel(activity);
        UUID entityUuid = action.entityUuid();
        if (level == null || entityUuid == null) {
            return mode == ModificationQueueMode.COMPLETING
                ? PrismAxiomContext.skippedResult(activity)
                : PrismAxiomContext.defaultResult(activity, mode);
        }

        String recordedSnapshot = action.previousState() != null ? action.previousState() : action.nextState();
        List<Entity> recordedEntities = decodeEntityTree(level, action, recordedSnapshot);
        if (recordedEntities == null || !validateLiveEntityTypes(level, recordedEntities)) {
            return PrismAxiomContext.erroredResult(activity);
        }

        Entity existingEntity = level.getEntity(entityUuid);
        if (existingEntity != null && !entityTypeMatches(action, existingEntity)) {
            AxiomPaper.PLUGIN.getLogger().warning(
                "Prism entity activity UUID belongs to a different live entity type"
            );
            return PrismAxiomContext.erroredResult(activity);
        }

        List<Entity> affectedEntities = new ArrayList<>(recordedEntities);
        affectedEntities.addAll(liveEntityTreesForRecorded(level, recordedEntities));
        String blacklistTarget = blacklistedEntityTarget(modificationRuleset, affectedEntities);
        if (blacklistTarget != null) {
            return blacklistedResult(activity, blacklistTarget);
        }

        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        if (entitySnapshot == null) {
            Set<UUID> recordedUuids = entityUuids(recordedEntities);
            boolean anyRecordedEntityExists = recordedUuids.stream().anyMatch(uuid -> level.getEntity(uuid) != null);
            if (!anyRecordedEntityExists && !modificationRuleset.overwrite()) {
                return alreadySetResult(activity, entityTranslationKey(action));
            }
            if (existingEntity != null) {
                discardRecordedEntityTree(existingEntity, recordedUuids);
            }
            for (UUID recordedUuid : recordedUuids) {
                Entity remainingEntity = level.getEntity(recordedUuid);
                if (remainingEntity != null) {
                    discardRecordedEntityTree(remainingEntity, recordedUuids);
                }
            }
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        try {
            Entity restoredEntity = recordedEntities.getFirst();

            String existingSnapshot = existingEntity == null
                ? null
                : PrismAxiomSerialization.captureEntitySnapshot(existingEntity);
            if (existingEntity != null && existingSnapshot == null) {
                return PrismAxiomContext.erroredResult(activity);
            }
            if (!modificationRuleset.overwrite() && java.util.Objects.equals(existingSnapshot, entitySnapshot)) {
                return alreadySetResult(activity, entityTranslationKey(action));
            }

            List<Entity> replacementEntities = restoredEntity.getSelfAndPassengers().toList();
            if (!canReplaceEntityTree(level, existingEntity, replacementEntities)) {
                return PrismAxiomContext.erroredResult(activity);
            }

            try {
                if (existingEntity != null) {
                    discardRecordedEntityTree(existingEntity, entityUuids(replacementEntities));
                }
                level.tryAddFreshEntityWithPassengers(restoredEntity);
                if (!entityTreeWasAdded(level, replacementEntities)) {
                    throw new IllegalStateException("Another plugin rejected a restored entity");
                }
            } catch (Exception exception) {
                discardAddedEntities(level, replacementEntities);
                restoreEntitySnapshot(level, existingSnapshot);
                throw exception;
            }
            return PrismAxiomContext.defaultResult(activity, mode);
        } catch (Exception exception) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to restore entity snapshot for Prism: " + exception.getMessage());
            return PrismAxiomContext.erroredResult(activity);
        }
    }

    private static ModificationResult restoreDeletedEntity(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        PrismAxiomActions.EntitySnapshot action
    ) {
        String rootBlacklistTarget = blacklistedActionEntityTarget(modificationRuleset, action);
        if (rootBlacklistTarget != null) {
            return blacklistedResult(activity, rootBlacklistTarget);
        }

        ServerLevel level = PrismAxiomContext.serverLevel(activity);
        List<Entity> recordedEntities = level == null
            ? null
            : decodeEntityTree(level, action, action.previousState());
        if (level == null || recordedEntities == null || !validateLiveEntityTypes(level, recordedEntities)) {
            return PrismAxiomContext.erroredResult(activity);
        }

        List<Entity> affectedEntities = new ArrayList<>(recordedEntities);
        affectedEntities.addAll(liveEntityTreesForRecorded(level, recordedEntities));
        Entity externalVehicle = externalVehicle(level, action);
        if (action.externalVehicleUuid() != null && externalVehicle == null) {
            return PrismAxiomContext.skippedResult(activity);
        }
        if (externalVehicle != null) {
            affectedEntities.add(externalVehicle);
        }
        String blacklistTarget = blacklistedEntityTarget(modificationRuleset, affectedEntities);
        if (blacklistTarget != null) {
            return blacklistedResult(activity, blacklistTarget);
        }
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        Entity liveRoot = level.getEntity(action.entityUuid());
        String liveSnapshot = liveRoot == null ? null : PrismAxiomSerialization.captureEntitySnapshot(liveRoot);
        if (liveRoot != null && liveSnapshot == null) {
            return PrismAxiomContext.erroredResult(activity);
        }
        boolean vehicleMatches = !action.externalVehicleRecorded()
            || (liveRoot != null && liveRoot.getVehicle() == externalVehicle);
        if (!modificationRuleset.overwrite()
            && java.util.Objects.equals(liveSnapshot, action.previousState())
            && vehicleMatches) {
            return alreadySetResult(activity, entityTranslationKey(action));
        }

        java.util.Map<UUID, UUID> desiredVehicles = new java.util.LinkedHashMap<>();
        for (Entity parent : recordedEntities) {
            for (Entity passenger : parent.getPassengers()) {
                desiredVehicles.put(passenger.getUUID(), parent.getUUID());
            }
        }
        for (int index = recordedEntities.size() - 1; index > 0; index--) {
            recordedEntities.get(index).stopRiding();
        }

        java.util.Map<UUID, Entity> desiredEntities = new java.util.LinkedHashMap<>();
        java.util.Map<UUID, Entity> recordedEntitiesByUuid = new java.util.LinkedHashMap<>();
        List<Entity> addedEntities = new ArrayList<>();
        for (Entity recordedEntity : recordedEntities) {
            Entity liveEntity = level.getEntity(recordedEntity.getUUID());
            Entity desiredEntity = liveEntity == null ? recordedEntity : liveEntity;
            recordedEntitiesByUuid.put(recordedEntity.getUUID(), recordedEntity);
            desiredEntities.put(recordedEntity.getUUID(), desiredEntity);
            if (liveEntity == null) {
                addedEntities.add(desiredEntity);
            }
        }

        java.util.Map<Entity, Entity> originalVehicles = new java.util.IdentityHashMap<>();
        java.util.Map<Entity, List<Entity>> originalPassengers = new java.util.IdentityHashMap<>();
        java.util.Map<Entity, String> originalStates = new java.util.IdentityHashMap<>();
        for (Entity desiredEntity : desiredEntities.values()) {
            if (!addedEntities.contains(desiredEntity)) {
                captureEntityTopology(desiredEntity, originalPassengers, originalVehicles);
                String originalState = PrismAxiomSerialization.captureEntityState(desiredEntity);
                if (originalState == null) {
                    return PrismAxiomContext.erroredResult(activity);
                }
                originalStates.put(desiredEntity, originalState);
            }
        }
        if (externalVehicle != null) {
            captureEntityTopology(externalVehicle, originalPassengers, originalVehicles);
        }

        try {
            for (java.util.Map.Entry<UUID, Entity> entry : desiredEntities.entrySet()) {
                Entity desiredEntity = entry.getValue();
                if (!addedEntities.contains(desiredEntity)) {
                    copyEntityState(recordedEntitiesByUuid.get(entry.getKey()), desiredEntity);
                }
            }
            for (Entity entity : addedEntities) {
                level.tryAddFreshEntityWithPassengers(entity);
                if (level.getEntity(entity.getUUID()) != entity) {
                    throw new IllegalStateException("Another plugin rejected a restored entity");
                }
            }
            applyDesiredTopology(desiredEntities, desiredVehicles);
            if (externalVehicle != null && !desiredEntities.get(action.entityUuid()).startRiding(externalVehicle, true, false)) {
                throw new IllegalStateException("Another plugin rejected the restored entity vehicle");
            }
            return PrismAxiomContext.defaultResult(activity, mode);
        } catch (Exception exception) {
            discardAddedEntities(level, addedEntities);
            for (java.util.Map.Entry<Entity, String> entry : originalStates.entrySet()) {
                restoreEntityState(level, entry.getKey(), entry.getValue());
            }
            if (!restoreEntityTopology(originalPassengers, originalVehicles)) {
                AxiomPaper.PLUGIN.getLogger().warning(
                    "Failed to fully recover entity passengers after a Prism restore error"
                );
            }
            AxiomPaper.PLUGIN.getLogger().warning(
                "Failed to restore a deleted entity for Prism: " + exception.getMessage()
            );
            return PrismAxiomContext.erroredResult(activity);
        }
    }

    private static void captureEntityTopology(
        Entity entity,
        java.util.Map<Entity, List<Entity>> originalPassengers,
        java.util.Map<Entity, Entity> originalVehicles
    ) {
        originalVehicles.putIfAbsent(entity, entity.getVehicle());
        capturePassengerList(entity, originalPassengers, originalVehicles);
        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            capturePassengerList(vehicle, originalPassengers, originalVehicles);
        }
    }

    private static void capturePassengerList(
        Entity vehicle,
        java.util.Map<Entity, List<Entity>> originalPassengers,
        java.util.Map<Entity, Entity> originalVehicles
    ) {
        List<Entity> passengers = List.copyOf(vehicle.getPassengers());
        originalPassengers.putIfAbsent(vehicle, passengers);
        for (Entity passenger : passengers) {
            originalVehicles.putIfAbsent(passenger, vehicle);
        }
    }

    private static ModificationResult removeDeletedEntity(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        PrismAxiomActions.EntitySnapshot action
    ) {
        String rootBlacklistTarget = blacklistedActionEntityTarget(modificationRuleset, action);
        if (rootBlacklistTarget != null) {
            return blacklistedResult(activity, rootBlacklistTarget);
        }

        ServerLevel level = PrismAxiomContext.serverLevel(activity);
        List<Entity> recordedEntities = level == null
            ? null
            : decodeEntityTree(level, action, action.previousState());
        if (level == null || recordedEntities == null || !validateLiveEntityTypes(level, recordedEntities)) {
            return PrismAxiomContext.erroredResult(activity);
        }

        List<Entity> affectedEntities = new ArrayList<>(recordedEntities);
        affectedEntities.addAll(liveEntityTreesForRecorded(level, recordedEntities));
        String blacklistTarget = blacklistedEntityTarget(modificationRuleset, affectedEntities);
        if (blacklistTarget != null) {
            return blacklistedResult(activity, blacklistTarget);
        }
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        Entity root = level.getEntity(action.entityUuid());
        if (root == null) {
            return modificationRuleset.overwrite()
                ? PrismAxiomContext.defaultResult(activity, mode)
                : alreadySetResult(activity, entityTranslationKey(action));
        }
        if (!entityTypeMatches(action, root)) {
            return PrismAxiomContext.erroredResult(activity);
        }
        // Match the Axiom delete operation: the root is removed while its passengers survive and dismount.
        root.discard();
        return PrismAxiomContext.defaultResult(activity, mode);
    }

    private static boolean restoreEntityTopology(
        java.util.Map<Entity, List<Entity>> originalPassengers,
        java.util.Map<Entity, Entity> originalVehicles
    ) {
        boolean restored = true;
        for (Entity entity : originalPassengers.keySet()) {
            if (!entity.isRemoved()) {
                restored &= safelyEjectPassengers(entity);
            }
        }
        for (java.util.Map.Entry<Entity, Entity> entry : originalVehicles.entrySet()) {
            Entity entity = entry.getKey();
            if (!entity.isRemoved() && entity.getVehicle() != entry.getValue()) {
                restored &= safelyStopRiding(entity);
            }
        }
        for (java.util.Map.Entry<Entity, List<Entity>> entry : originalPassengers.entrySet()) {
            Entity vehicle = entry.getKey();
            if (vehicle.isRemoved()) {
                continue;
            }
            for (Entity passenger : entry.getValue()) {
                if (!passenger.isRemoved() && passenger.getVehicle() == null) {
                    restored &= safelyStartRiding(passenger, vehicle);
                }
            }
        }
        return restored;
    }

    private static boolean safelyEjectPassengers(Entity entity) {
        try {
            entity.ejectPassengers();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean safelyStartRiding(Entity passenger, Entity vehicle) {
        try {
            return passenger.startRiding(vehicle, true, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean safelyStopRiding(Entity entity) {
        try {
            entity.stopRiding();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void restoreEntityState(
        ServerLevel level,
        @Nullable Entity entity,
        @Nullable String snapshot
    ) {
        if (entity == null || snapshot == null || entity.isRemoved()) {
            return;
        }
        try {
            Entity decoded = decodeEntityTreeRoot(level, snapshot);
            if (decoded == null || decoded.getType() != entity.getType()
                || !decoded.getUUID().equals(entity.getUUID())) {
                throw new IllegalStateException("Previous entity state identity does not match");
            }
            copyEntityState(decoded, entity);
        } catch (Exception exception) {
            AxiomPaper.PLUGIN.getLogger().warning(
                "Failed to recover entity state after a Prism restore error: " + exception.getMessage()
            );
        }
    }

    @Nullable
    private static List<Entity> decodeEntityTree(
        ServerLevel level,
        PrismAxiomActions.EntitySnapshot action,
        @Nullable String snapshot
    ) {
        if (snapshot == null) {
            AxiomPaper.PLUGIN.getLogger().warning("Prism entity activity is missing its recorded snapshot");
            return null;
        }
        try {
            Entity root = decodeEntityTreeRoot(level, snapshot);
            if (root == null || !root.getUUID().equals(action.entityUuid()) || !entityTypeMatches(action, root)) {
                AxiomPaper.PLUGIN.getLogger().warning("Prism entity activity snapshot identity does not match");
                return null;
            }
            List<Entity> entities = root.getSelfAndPassengers().toList();
            return entityUuids(entities).size() == entities.size() ? entities : null;
        } catch (Exception exception) {
            AxiomPaper.PLUGIN.getLogger().warning(
                "Failed to decode Prism entity activity snapshot: " + exception.getMessage()
            );
            return null;
        }
    }

    @Nullable
    private static Entity decodeEntityTreeRoot(ServerLevel level, String snapshot) throws Exception {
        CompoundTag entityTag = TagParser.parseCompoundFully(snapshot);
        return net.minecraft.world.entity.EntityType.loadEntityRecursive(
            entityTag,
            level,
            new EntitySpawnRequest(EntitySpawnReason.COMMAND, true),
            loadedEntity -> loadedEntity
        );
    }

    private static Set<UUID> entityUuids(List<Entity> entities) {
        Set<UUID> uuids = new HashSet<>();
        entities.stream().map(Entity::getUUID).forEach(uuids::add);
        return uuids;
    }

    private static boolean validateLiveEntityTypes(ServerLevel level, List<Entity> recordedEntities) {
        for (Entity recordedEntity : recordedEntities) {
            Entity liveEntity = level.getEntity(recordedEntity.getUUID());
            if (liveEntity != null && liveEntity.getType() != recordedEntity.getType()) {
                AxiomPaper.PLUGIN.getLogger().warning(
                    "Prism entity activity UUID belongs to a different live entity type"
                );
                return false;
            }
        }
        return true;
    }

    private static List<Entity> liveEntityTreesForRecorded(ServerLevel level, List<Entity> recordedEntities) {
        java.util.Map<UUID, Entity> liveEntities = new java.util.LinkedHashMap<>();
        for (Entity recordedEntity : recordedEntities) {
            Entity liveEntity = level.getEntity(recordedEntity.getUUID());
            if (liveEntity != null) {
                liveEntity.getSelfAndPassengers().forEach(entity -> liveEntities.putIfAbsent(entity.getUUID(), entity));
                Entity vehicle = liveEntity.getVehicle();
                if (vehicle != null) {
                    liveEntities.putIfAbsent(vehicle.getUUID(), vehicle);
                }
            }
        }
        return List.copyOf(liveEntities.values());
    }

    @Nullable
    private static Entity externalVehicle(ServerLevel level, PrismAxiomActions.EntitySnapshot action) {
        UUID externalVehicleUuid = action.externalVehicleUuid();
        return externalVehicleUuid == null ? null : level.getEntity(externalVehicleUuid);
    }

    @Nullable
    private static String blacklistedActionEntityTarget(
        ModificationRuleset modificationRuleset,
        PrismAxiomActions.EntitySnapshot action
    ) {
        if (action.entityContainer() instanceof PaperEntityContainer entityContainer
            && modificationRuleset.entityBlacklistContainsAny(entityContainer.entityType().toString())) {
            return entityContainer.translationKey();
        }
        return null;
    }

    @Nullable
    private static String blacklistedActionEntityTarget(
        ModificationRuleset modificationRuleset,
        PrismAxiomActions.EntityPassengers action
    ) {
        if (action.entityContainer() instanceof PaperEntityContainer entityContainer
            && modificationRuleset.entityBlacklistContainsAny(entityContainer.entityType().toString())) {
            return entityContainer.translationKey();
        }
        return null;
    }

    @Nullable
    private static String blacklistedEntityTarget(
        ModificationRuleset modificationRuleset,
        Iterable<Entity> entities
    ) {
        for (Entity entity : entities) {
            org.bukkit.entity.EntityType entityType = entity.getBukkitEntity().getType();
            if (modificationRuleset.entityBlacklistContainsAny(entityType.toString())) {
                return entityType.translationKey();
            }
        }
        return null;
    }

    private static ModificationResult blacklistedResult(Activity activity, String target) {
        return ModificationResult.builder()
            .activity(activity)
            .skipped()
            .skipReason(ModificationSkipReason.BLACKLISTED)
            .target(target)
            .build();
    }

    @Nullable
    private static String entityTranslationKey(PrismAxiomActions.EntitySnapshot action) {
        return action.entityContainer() instanceof PaperEntityContainer entityContainer
            ? entityContainer.translationKey()
            : null;
    }

    @Nullable
    private static String entityTranslationKey(PrismAxiomActions.EntityPassengers action) {
        return action.entityContainer() instanceof PaperEntityContainer entityContainer
            ? entityContainer.translationKey()
            : null;
    }

    private static boolean entityTypeMatches(PrismAxiomActions.EntitySnapshot action, Entity entity) {
        return !(action.entityContainer() instanceof PaperEntityContainer entityContainer)
            || entity.getBukkitEntity().getType() == entityContainer.entityType();
    }

    private static boolean canReplaceEntityTree(
        ServerLevel level,
        @Nullable Entity existingRoot,
        List<Entity> replacements
    ) {
        Set<UUID> existingUuids = new HashSet<>();
        if (existingRoot != null) {
            existingRoot.getSelfAndPassengers().map(Entity::getUUID).forEach(existingUuids::add);
        }

        Set<UUID> replacementUuids = new HashSet<>();
        for (Entity replacement : replacements) {
            if (!replacementUuids.add(replacement.getUUID())) {
                AxiomPaper.PLUGIN.getLogger().warning("Prism entity snapshot contains duplicate entity UUIDs");
                return false;
            }
            Entity conflicting = level.getEntity(replacement.getUUID());
            if (conflicting != null && !existingUuids.contains(conflicting.getUUID())) {
                AxiomPaper.PLUGIN.getLogger().warning(
                    "Prism entity snapshot conflicts with an unrelated existing entity UUID"
                );
                return false;
            }
            if (conflicting != null && conflicting.getType() != replacement.getType()) {
                AxiomPaper.PLUGIN.getLogger().warning(
                    "Prism entity snapshot conflicts with a different live entity type"
                );
                return false;
            }
        }
        return true;
    }

    private static boolean entityTreeWasAdded(ServerLevel level, List<Entity> entities) {
        return entities.stream().allMatch(entity -> level.getEntity(entity.getUUID()) == entity);
    }

    private static void discardAddedEntities(ServerLevel level, List<Entity> entities) {
        for (int index = entities.size() - 1; index >= 0; index--) {
            Entity entity = entities.get(index);
            if (level.getEntity(entity.getUUID()) == entity) {
                entity.discard();
            }
        }
    }

    private static void restoreEntitySnapshot(ServerLevel level, @Nullable String snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            Entity restoredRoot = decodeEntityTreeRoot(level, snapshot);
            if (restoredRoot == null) {
                throw new IllegalStateException("Unable to decode previous entity snapshot");
            }

            List<Entity> restoredEntities = restoredRoot.getSelfAndPassengers().toList();
            if (entityUuids(restoredEntities).size() != restoredEntities.size()
                || !validateLiveEntityTypes(level, restoredEntities)) {
                throw new IllegalStateException("Previous entity snapshot has conflicting identities");
            }

            java.util.Map<UUID, UUID> desiredVehicles = captureDesiredVehicles(restoredEntities);
            disconnectEntityTree(restoredEntities);

            java.util.Map<UUID, Entity> desiredEntities = new java.util.LinkedHashMap<>();
            List<Entity> addedEntities = new ArrayList<>();
            for (Entity restoredEntity : restoredEntities) {
                Entity liveEntity = level.getEntity(restoredEntity.getUUID());
                Entity desiredEntity = liveEntity == null ? restoredEntity : liveEntity;
                desiredEntities.put(restoredEntity.getUUID(), desiredEntity);
                if (liveEntity == null) {
                    addedEntities.add(desiredEntity);
                }
            }

            java.util.Map<Entity, List<Entity>> originalPassengers = new java.util.IdentityHashMap<>();
            java.util.Map<Entity, Entity> originalVehicles = new java.util.IdentityHashMap<>();
            java.util.Map<Entity, String> originalStates = new java.util.IdentityHashMap<>();
            for (Entity desiredEntity : desiredEntities.values()) {
                if (addedEntities.contains(desiredEntity)) {
                    continue;
                }
                captureEntityTopology(desiredEntity, originalPassengers, originalVehicles);
                String originalState = PrismAxiomSerialization.captureEntityState(desiredEntity);
                if (originalState == null) {
                    throw new IllegalStateException("Unable to capture live entity before recovery");
                }
                originalStates.put(desiredEntity, originalState);
            }

            try {
                for (Entity restoredEntity : restoredEntities) {
                    Entity desiredEntity = desiredEntities.get(restoredEntity.getUUID());
                    if (!addedEntities.contains(desiredEntity)) {
                        copyEntityState(restoredEntity, desiredEntity);
                    }
                }
                for (Entity addedEntity : addedEntities) {
                    level.tryAddFreshEntityWithPassengers(addedEntity);
                    if (level.getEntity(addedEntity.getUUID()) != addedEntity) {
                        throw new IllegalStateException("Another plugin rejected the previous entity snapshot");
                    }
                }
                applyDesiredTopology(desiredEntities, desiredVehicles);
            } catch (Exception exception) {
                discardAddedEntities(level, addedEntities);
                for (java.util.Map.Entry<Entity, String> entry : originalStates.entrySet()) {
                    restoreEntityState(level, entry.getKey(), entry.getValue());
                }
                if (!restoreEntityTopology(originalPassengers, originalVehicles)) {
                    AxiomPaper.PLUGIN.getLogger().warning(
                        "Failed to fully recover entity passengers after a Prism recovery error"
                    );
                }
                throw exception;
            }
        } catch (Exception exception) {
            AxiomPaper.PLUGIN.getLogger().warning(
                "Failed to recover an entity after Prism restore failure: " + exception.getMessage()
            );
        }
    }

    private static java.util.Map<UUID, UUID> captureDesiredVehicles(List<Entity> entities) {
        java.util.Map<UUID, UUID> desiredVehicles = new java.util.LinkedHashMap<>();
        for (Entity parent : entities) {
            for (Entity passenger : parent.getPassengers()) {
                desiredVehicles.put(passenger.getUUID(), parent.getUUID());
            }
        }
        return desiredVehicles;
    }

    private static void disconnectEntityTree(List<Entity> entities) {
        for (int index = entities.size() - 1; index > 0; index--) {
            entities.get(index).stopRiding();
        }
    }

    private static void applyDesiredTopology(
        java.util.Map<UUID, Entity> desiredEntities,
        java.util.Map<UUID, UUID> desiredVehicles
    ) {
        for (Entity desiredEntity : desiredEntities.values()) {
            desiredEntity.stopRiding();
            desiredEntity.ejectPassengers();
        }
        for (java.util.Map.Entry<UUID, UUID> edge : desiredVehicles.entrySet()) {
            Entity passenger = desiredEntities.get(edge.getKey());
            Entity vehicle = desiredEntities.get(edge.getValue());
            if (passenger == null || vehicle == null || !passenger.startRiding(vehicle, true, false)) {
                throw new IllegalStateException("Another plugin rejected restored entity passengers");
            }
        }
    }

    private static void copyEntityState(Entity source, Entity target) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, source.registryAccess());
        source.saveWithoutId(output);
        CompoundTag state = output.buildResult();
        state.remove("Passengers");
        target.load(TagValueInput.create(ProblemReporter.DISCARDING, target.registryAccess(), state));
    }

    private static ModificationResult applyEntityState(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        PrismAxiomActions.EntitySnapshot action,
        String entitySnapshot
    ) {
        String blacklistTarget = blacklistedActionEntityTarget(modificationRuleset, action);
        if (blacklistTarget != null) {
            return blacklistedResult(activity, blacklistTarget);
        }
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        ServerLevel level = PrismAxiomContext.serverLevel(activity);
        Entity existingEntity = level == null ? null : level.getEntity(action.entityUuid());
        if (existingEntity == null) {
            return PrismAxiomContext.skippedResult(activity);
        }
        if (!entityTypeMatches(action, existingEntity)) {
            return PrismAxiomContext.erroredResult(activity);
        }

        String currentSnapshot = PrismAxiomSerialization.captureEntityState(existingEntity);
        if (currentSnapshot == null) {
            return PrismAxiomContext.erroredResult(activity);
        }
        if (!modificationRuleset.overwrite() && java.util.Objects.equals(currentSnapshot, entitySnapshot)) {
            return alreadySetResult(activity, entityTranslationKey(action));
        }

        try {
            CompoundTag entityTag = TagParser.parseCompoundFully(entitySnapshot);
            Entity decodedEntity = net.minecraft.world.entity.EntityType.loadEntityRecursive(
                entityTag,
                level,
                new EntitySpawnRequest(EntitySpawnReason.COMMAND, true),
                loadedEntity -> loadedEntity
            );
            if (decodedEntity == null || !decodedEntity.getUUID().equals(action.entityUuid())
                || decodedEntity.getType() != existingEntity.getType() || !entityTypeMatches(action, decodedEntity)) {
                return PrismAxiomContext.erroredResult(activity);
            }

            copyEntityState(decodedEntity, existingEntity);
            return PrismAxiomContext.defaultResult(activity, mode);
        } catch (Exception exception) {
            restoreEntityState(level, existingEntity, currentSnapshot);
            AxiomPaper.PLUGIN.getLogger().warning("Failed to restore entity state for Prism: " + exception.getMessage());
            return PrismAxiomContext.erroredResult(activity);
        }
    }

    private static ModificationResult applyEntityPassengers(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        PrismAxiomActions.EntityPassengers action,
        String serializedPassengers
    ) {
        String rootBlacklistTarget = blacklistedActionEntityTarget(modificationRuleset, action);
        if (rootBlacklistTarget != null) {
            return blacklistedResult(activity, rootBlacklistTarget);
        }

        ServerLevel level = PrismAxiomContext.serverLevel(activity);
        Entity vehicle = level == null ? null : level.getEntity(action.entityUuid());
        if (vehicle == null) {
            return mode == ModificationQueueMode.COMPLETING
                ? PrismAxiomContext.skippedResult(activity)
                : PrismAxiomContext.defaultResult(activity, mode);
        }
        if (action.entityContainer() instanceof PaperEntityContainer entityContainer
            && vehicle.getBukkitEntity().getType() != entityContainer.entityType()) {
            return PrismAxiomContext.erroredResult(activity);
        }

        try {
            java.util.List<UUID> passengerUuids = serializedPassengers.isEmpty()
                ? java.util.List.of()
                : java.util.Arrays.stream(serializedPassengers.split(",", -1)).map(UUID::fromString).toList();
            if (new HashSet<>(passengerUuids).size() != passengerUuids.size()) {
                throw new IllegalArgumentException("Duplicate Prism entity passenger UUID");
            }
            java.util.List<Entity> passengers = new java.util.ArrayList<>(passengerUuids.size());
            boolean missingPassenger = false;
            for (UUID passengerUuid : passengerUuids) {
                Entity passenger = level.getEntity(passengerUuid);
                if (passenger == null) {
                    missingPassenger = true;
                    continue;
                }
                if (passenger == vehicle || passenger.getSelfAndPassengers().anyMatch(vehicle::equals)) {
                    return PrismAxiomContext.erroredResult(activity);
                }
                passengers.add(passenger);
            }

            List<Entity> originalPassengers = new ArrayList<>(vehicle.getPassengers());
            List<Entity> affectedEntities = new ArrayList<>();
            vehicle.getSelfAndPassengers().forEach(affectedEntities::add);
            for (Entity passenger : passengers) {
                passenger.getSelfAndPassengers().forEach(affectedEntities::add);
                Entity originalVehicle = passenger.getVehicle();
                if (originalVehicle != null) {
                    affectedEntities.add(originalVehicle);
                }
            }
            String blacklistTarget = blacklistedEntityTarget(modificationRuleset, affectedEntities);
            if (blacklistTarget != null) {
                return blacklistedResult(activity, blacklistTarget);
            }
            if (mode != ModificationQueueMode.COMPLETING) {
                return PrismAxiomContext.defaultResult(activity, mode);
            }
            if (missingPassenger) {
                return PrismAxiomContext.skippedResult(activity);
            }
            if (!modificationRuleset.overwrite() && samePassengerOrder(originalPassengers, passengers)) {
                return alreadySetResult(activity, entityTranslationKey(action));
            }

            java.util.Map<Entity, List<Entity>> originalPassengerLists = new java.util.IdentityHashMap<>();
            java.util.Map<Entity, Entity> originalVehicles = new java.util.IdentityHashMap<>();
            captureEntityTopology(vehicle, originalPassengerLists, originalVehicles);
            for (Entity passenger : passengers) {
                captureEntityTopology(passenger, originalPassengerLists, originalVehicles);
            }

            try {
                vehicle.ejectPassengers();
                for (Entity passenger : passengers) {
                    if (!passenger.startRiding(vehicle, true, false)) {
                        throw new IllegalStateException("Another plugin rejected restored entity passengers");
                    }
                }
            } catch (Exception exception) {
                if (!restoreEntityTopology(originalPassengerLists, originalVehicles)) {
                    AxiomPaper.PLUGIN.getLogger().warning(
                        "Failed to fully recover entity passengers after a Prism modification error"
                    );
                }
                throw exception;
            }
            return PrismAxiomContext.defaultResult(activity, mode);
        } catch (Exception exception) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to restore entity passengers for Prism: " + exception.getMessage());
            return PrismAxiomContext.erroredResult(activity);
        }
    }

    private static boolean samePassengerOrder(List<Entity> first, List<Entity> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (first.get(index) != second.get(index)) {
                return false;
            }
        }
        return true;
    }

    private static ModificationResult alreadySetResult(Activity activity) {
        return ModificationResult.builder()
            .activity(activity)
            .skipped()
            .skipReason(ModificationSkipReason.ALREADY_SET)
            .build();
    }

    private static ModificationResult alreadySetResult(Activity activity, @Nullable String translationKey) {
        var builder = ModificationResult.builder()
            .activity(activity)
            .skipped()
            .skipReason(ModificationSkipReason.ALREADY_SET);
        if (translationKey != null) {
            builder.target(translationKey);
        }
        return builder.build();
    }

    private static void discardRecordedEntityTree(Entity entity, Set<UUID> recordedUuids) {
        for (Entity passenger : List.copyOf(entity.getPassengers())) {
            if (recordedUuids.contains(passenger.getUUID())) {
                discardRecordedEntityTree(passenger, recordedUuids);
            } else {
                passenger.stopRiding();
            }
        }
        entity.discard();
    }

    private static ModificationResult applyPlayerTeleport(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        String encodedLocation
    ) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
        Player player = PrismAxiomContext.onlinePlayer(action.playerContainer());
        if (player == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        var location = PrismAxiomSerialization.decodeLocation(encodedLocation);
        if (!modificationRuleset.overwrite() && PrismAxiomSerialization.sameLocation(player.getLocation(), location)) {
            return alreadySetResult(activity);
        }
        if (location.getWorld() == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        org.bukkit.Location previousLocation = player.getLocation();
        if (!player.teleport(location)) {
            return PrismAxiomContext.skippedResult(activity);
        }
        if (PrismAxiomSerialization.sameLocation(player.getLocation(), location)) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        player.teleport(previousLocation);
        return PrismAxiomContext.erroredResult(activity);
    }

    private static ModificationResult applyPlayerGamemode(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        String encodedGamemode
    ) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
        Player player = PrismAxiomContext.onlinePlayer(action.playerContainer());
        if (player == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        GameMode gameMode = GameMode.valueOf(encodedGamemode);
        if (!modificationRuleset.overwrite() && player.getGameMode() == gameMode) {
            return alreadySetResult(activity);
        }
        GameMode previousGameMode = player.getGameMode();
        player.setGameMode(gameMode);
        if (player.getGameMode() == gameMode) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        if (player.getGameMode() != previousGameMode) {
            player.setGameMode(previousGameMode);
        }
        return PrismAxiomContext.erroredResult(activity);
    }

    private static ModificationResult applyPlayerFlySpeed(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        String encodedFlySpeed
    ) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
        Player player = PrismAxiomContext.onlinePlayer(action.playerContainer());
        if (player == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        float flySpeed = Float.parseFloat(encodedFlySpeed);
        if (!Float.isFinite(flySpeed) || flySpeed < -1.0F || flySpeed > 1.0F) {
            throw new IllegalArgumentException("Invalid Prism fly speed state");
        }
        float currentFlySpeed = ((CraftPlayer) player).getHandle().getAbilities().getFlyingSpeed();
        if (!modificationRuleset.overwrite() && Float.compare(currentFlySpeed, flySpeed) == 0) {
            return alreadySetResult(activity);
        }
        ((CraftPlayer) player).getHandle().getAbilities().setFlyingSpeed(flySpeed);
        if (Float.compare(((CraftPlayer) player).getHandle().getAbilities().getFlyingSpeed(), flySpeed) == 0) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        ((CraftPlayer) player).getHandle().getAbilities().setFlyingSpeed(currentFlySpeed);
        return PrismAxiomContext.erroredResult(activity);
    }

    private static ModificationResult applyPlayerNoPhysicalTrigger(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        String encodedValue
    ) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) activity.action();
        Player player = PrismAxiomContext.onlinePlayer(action.playerContainer());
        if (player == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        if (!(encodedValue.equals("true") || encodedValue.equals("false"))) {
            throw new IllegalArgumentException("Invalid Prism no-physical-trigger state");
        }
        boolean noPhysicalTrigger = Boolean.parseBoolean(encodedValue);
        if (!modificationRuleset.overwrite()
            && AxiomPaper.PLUGIN.isNoPhysicalTrigger(player.getUniqueId()) == noPhysicalTrigger) {
            return alreadySetResult(activity);
        }
        AxiomPaper.PLUGIN.setNoPhysicalTrigger(player.getUniqueId(), noPhysicalTrigger);
        return AxiomPaper.PLUGIN.isNoPhysicalTrigger(player.getUniqueId()) == noPhysicalTrigger
            ? PrismAxiomContext.defaultResult(activity, mode)
            : PrismAxiomContext.erroredResult(activity);
    }

    private static ModificationResult applyWorldTime(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        String encodedState
    ) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        World world = PrismAxiomContext.world(activity);
        if (world == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        PrismAxiomSerialization.WorldTimeState worldTimeState = PrismAxiomSerialization.decodeWorldTimeState(encodedState);
        long previousTime = world.getTime();
        boolean daylightCycleEnabled = Boolean.TRUE.equals(world.getGameRuleValue(org.bukkit.GameRules.ADVANCE_TIME));
        if (!modificationRuleset.overwrite()
            && world.getTime() == worldTimeState.time()
            && daylightCycleEnabled == worldTimeState.daylightCycleEnabled()) {
            return alreadySetResult(activity);
        }
        world.setTime(worldTimeState.time());
        boolean gameRuleUpdated = world.setGameRule(
            org.bukkit.GameRules.ADVANCE_TIME,
            worldTimeState.daylightCycleEnabled()
        );
        if (gameRuleUpdated && world.getTime() == worldTimeState.time()
            && Boolean.TRUE.equals(world.getGameRuleValue(org.bukkit.GameRules.ADVANCE_TIME))
                == worldTimeState.daylightCycleEnabled()) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        world.setTime(previousTime);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, daylightCycleEnabled);
        return PrismAxiomContext.erroredResult(activity);
    }

    private static ModificationResult applyWorldProperty(
        ModificationRuleset modificationRuleset,
        Object owner,
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

        var worldPropertyRegistry = AxiomPaper.PLUGIN.getOrCreateWorldProperties(world);
        if (worldPropertyRegistry == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        var worldPropertyHolder = worldPropertyRegistry.getById(VersionHelper.createIdentifier(propertyId));
        if (worldPropertyHolder == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        try {
            byte[] decodedValue = PrismAxiomSerialization.decodeBytes(encodedValue);
            if (!modificationRuleset.overwrite()
                && java.util.Arrays.equals(worldPropertyHolder.serializeValue(), decodedValue)) {
                return alreadySetResult(activity);
            }
            boolean updated = worldPropertyHolder.updateAndGetResult(
                PrismAxiomContext.actor(owner),
                world,
                decodedValue,
                true
            );
            return updated
                ? PrismAxiomContext.defaultResult(activity, mode)
                : PrismAxiomContext.skippedResult(activity);
        } catch (Exception exception) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to restore a Prism world property: " + exception.getMessage());
            return PrismAxiomContext.erroredResult(activity);
        }
    }

    private static ModificationResult applyAnnotationSnapshot(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        String encodedSnapshot
    ) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }
        if (!AxiomPaper.PLUGIN.allowAnnotations) {
            return PrismAxiomContext.skippedResult(activity);
        }

        World world = PrismAxiomContext.world(activity);
        if (world == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        byte[] actions = PrismAxiomSerialization.decodeBytes(encodedSnapshot);
        if (!modificationRuleset.overwrite() && ServerAnnotations.isNoOp(world, actions)) {
            return alreadySetResult(activity);
        }
        ServerAnnotations.applyActions(world, actions);
        return PrismAxiomContext.defaultResult(activity, mode);
    }

    private static ModificationResult applyBiome(
        ModificationRuleset modificationRuleset,
        Activity activity,
        ModificationQueueMode mode,
        String biomeName
    ) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return PrismAxiomContext.defaultResult(activity, mode);
        }

        ServerLevel level = PrismAxiomContext.serverLevel(activity);
        Identifier biomeId = Identifier.tryParse(biomeName);
        if (level == null || biomeId == null) {
            return PrismAxiomContext.skippedResult(activity);
        }

        var biomeRegistry = level.registryAccess().lookup(Registries.BIOME);
        if (biomeRegistry.isEmpty()) {
            return PrismAxiomContext.skippedResult(activity);
        }

        var biomeHolder = biomeRegistry.get().get(biomeId);
        if (biomeHolder.isEmpty()) {
            return PrismAxiomContext.skippedResult(activity);
        }

        var coordinate = activity.coordinate();
        int quartX = coordinate.intX() >> 2;
        int quartY = coordinate.intY() >> 2;
        int quartZ = coordinate.intZ() >> 2;
        int sectionY = quartY >> 2;
        if (sectionY < level.getMinSectionY() || sectionY >= level.getMaxSectionY()) {
            return PrismAxiomContext.skippedResult(activity);
        }

        LevelChunk chunk = level.getChunk(quartX >> 2, quartZ >> 2);

        var section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));
        @SuppressWarnings("unchecked")
        PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();
        Holder<Biome> nextBiome = biomeHolder.get();
        Holder<Biome> currentBiome = biomes.get(quartX & 3, quartY & 3, quartZ & 3);
        if (!modificationRuleset.overwrite() && currentBiome.is(nextBiome)) {
            return alreadySetResult(activity, biomeId.toLanguageKey("biome"));
        }
        biomes.set(quartX & 3, quartY & 3, quartZ & 3, nextBiome);
        chunk.markUnsaved();
        PrismAxiomContext.queueBiomeUpdate(chunk);
        return PrismAxiomContext.defaultResult(activity, mode);
    }
}
