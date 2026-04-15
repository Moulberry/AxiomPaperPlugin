package com.moulberry.axiom.integration.prism;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;
import org.prism_mc.prism.api.actions.Action;
import org.prism_mc.prism.api.actions.types.ActionResultType;
import org.prism_mc.prism.api.actions.types.ActionType;
import org.prism_mc.prism.api.containers.PlayerContainer;
import org.prism_mc.prism.paper.api.PrismPaperApi;
import org.prism_mc.prism.paper.api.activities.PaperActivity;
import org.prism_mc.prism.paper.api.containers.PaperEntityContainer;

import net.minecraft.world.entity.Entity;

public final class PrismAxiomIntegration {
    private static final String ENTITY_SPAWN_ACTION_KEY = "axiom-entity-spawn";
    private static final String ENTITY_DELETE_ACTION_KEY = "axiom-entity-delete";
    private static final String ENTITY_MODIFY_ACTION_KEY = "axiom-entity-modify";
    private static final String PLAYER_TELEPORT_ACTION_KEY = "axiom-player-teleport";
    private static final String PLAYER_GAMEMODE_ACTION_KEY = "axiom-player-gamemode";
    private static final String PLAYER_FLY_SPEED_ACTION_KEY = "axiom-player-fly-speed";
    private static final String PLAYER_NO_PHYSICAL_TRIGGER_ACTION_KEY = "axiom-player-no-physical-trigger";
    private static final String WORLD_TIME_ACTION_KEY = "axiom-world-time";
    private static final String WORLD_PROPERTY_ACTION_KEY = "axiom-world-property";
    private static final String ANNOTATION_SNAPSHOT_ACTION_KEY = "axiom-annotation-snapshot";

    private static final PrismPaperApi PRISM_API = lookupPrismApi();

    private static final ActionType ENTITY_SPAWN_ACTION = registerActionType(
        PrismAxiomActionTypes.entitySnapshot(
            ENTITY_SPAWN_ACTION_KEY,
            ActionResultType.CREATES,
            new PrismAxiomHandlers.EntityCreateHandler()
        )
    );
    private static final ActionType ENTITY_DELETE_ACTION = registerActionType(
        PrismAxiomActionTypes.entitySnapshot(
            ENTITY_DELETE_ACTION_KEY,
            ActionResultType.REMOVES,
            new PrismAxiomHandlers.EntityDeleteHandler()
        )
    );
    private static final ActionType ENTITY_MODIFY_ACTION = registerActionType(
        PrismAxiomActionTypes.entitySnapshot(
            ENTITY_MODIFY_ACTION_KEY,
            ActionResultType.REPLACES,
            new PrismAxiomHandlers.EntityModifyHandler()
        )
    );

    private static final ActionType PLAYER_TELEPORT_ACTION = registerActionType(
        PrismAxiomActionTypes.playerState(
            PLAYER_TELEPORT_ACTION_KEY,
            new PrismAxiomHandlers.PlayerTeleportHandler()
        )
    );
    private static final ActionType PLAYER_GAMEMODE_ACTION = registerActionType(
        PrismAxiomActionTypes.playerState(
            PLAYER_GAMEMODE_ACTION_KEY,
            new PrismAxiomHandlers.PlayerGamemodeHandler()
        )
    );
    private static final ActionType PLAYER_FLY_SPEED_ACTION = registerActionType(
        PrismAxiomActionTypes.playerState(
            PLAYER_FLY_SPEED_ACTION_KEY,
            new PrismAxiomHandlers.PlayerFlySpeedHandler()
        )
    );
    private static final ActionType PLAYER_NO_PHYSICAL_TRIGGER_ACTION = registerActionType(
        PrismAxiomActionTypes.playerState(
            PLAYER_NO_PHYSICAL_TRIGGER_ACTION_KEY,
            new PrismAxiomHandlers.PlayerNoPhysicalTriggerHandler()
        )
    );

    private static final ActionType WORLD_TIME_ACTION = registerActionType(
        PrismAxiomActionTypes.genericState(
            WORLD_TIME_ACTION_KEY,
            new PrismAxiomHandlers.WorldTimeHandler()
        )
    );
    private static final ActionType WORLD_PROPERTY_ACTION = registerActionType(
        PrismAxiomActionTypes.genericState(
            WORLD_PROPERTY_ACTION_KEY,
            new PrismAxiomHandlers.WorldPropertyHandler()
        )
    );
    private static final ActionType ANNOTATION_SNAPSHOT_ACTION = registerActionType(
        PrismAxiomActionTypes.genericState(
            ANNOTATION_SNAPSHOT_ACTION_KEY,
            new PrismAxiomHandlers.AnnotationSnapshotHandler()
        )
    );

    private PrismAxiomIntegration() {
    }

    private static boolean shouldSkipLogging(PrismLoggingType prismLoggingType) {
        return PRISM_API == null || !PrismIntegrationImpl.isEnabled() || com.moulberry.axiom.AxiomPaper.PLUGIN == null
            || !com.moulberry.axiom.AxiomPaper.PLUGIN.shouldLogPrism(prismLoggingType);
    }

    public static void logEntitySpawn(Player actor, Entity entity) {
        if (shouldSkipLogging(PrismLoggingType.ENTITY_SPAWNS)) {
            return;
        }

        String entitySnapshot = captureEntitySnapshot(entity);
        if (entitySnapshot == null) {
            return;
        }

        record(
            new PrismAxiomActions.EntitySnapshot(
                ENTITY_SPAWN_ACTION,
                new PaperEntityContainer(entity.getBukkitEntity().getType()),
                entity.getUUID(),
                null,
                entitySnapshot
            ),
            actor,
            entity.getBukkitEntity().getWorld(),
            entity.getBukkitEntity().getLocation()
        );
    }

    public static void logEntityDelete(Player actor, Entity entity) {
        if (shouldSkipLogging(PrismLoggingType.ENTITY_DELETES)) {
            return;
        }

        String entitySnapshot = captureEntitySnapshot(entity);
        if (entitySnapshot == null) {
            return;
        }

        record(
            new PrismAxiomActions.EntitySnapshot(
                ENTITY_DELETE_ACTION,
                new PaperEntityContainer(entity.getBukkitEntity().getType()),
                entity.getUUID(),
                entitySnapshot,
                null
            ),
            actor,
            entity.getBukkitEntity().getWorld(),
            entity.getBukkitEntity().getLocation()
        );
    }

    public static void logEntityModification(Player actor, Entity entity, String previousSnapshot, String nextSnapshot) {
        if (shouldSkipLogging(PrismLoggingType.ENTITY_MODIFICATIONS) || java.util.Objects.equals(previousSnapshot, nextSnapshot)) {
            return;
        }

        record(
            new PrismAxiomActions.EntitySnapshot(
                ENTITY_MODIFY_ACTION,
                new PaperEntityContainer(entity.getBukkitEntity().getType()),
                entity.getUUID(),
                previousSnapshot,
                nextSnapshot
            ),
            actor,
            entity.getBukkitEntity().getWorld(),
            entity.getBukkitEntity().getLocation()
        );
    }

    public static void logPlayerTeleport(Player actor, Player targetPlayer, Location previousLocation, Location nextLocation) {
        if (shouldSkipLogging(PrismLoggingType.PLAYER_TELEPORTS) || PrismAxiomSerialization.sameLocation(previousLocation, nextLocation)) {
            return;
        }

        record(
            new PrismAxiomActions.PlayerState(
                PLAYER_TELEPORT_ACTION,
                new PlayerContainer(targetPlayer.getName(), targetPlayer.getUniqueId()),
                PrismAxiomSerialization.encodeLocation(previousLocation),
                PrismAxiomSerialization.encodeLocation(nextLocation),
                "teleport"
            ),
            actor,
            targetPlayer.getWorld(),
            nextLocation
        );
    }

    public static void logPlayerGamemode(Player actor, Player targetPlayer, GameMode previousMode, GameMode nextMode) {
        if (shouldSkipLogging(PrismLoggingType.PLAYER_GAMEMODE_CHANGES) || previousMode == nextMode) {
            return;
        }

        record(
            new PrismAxiomActions.PlayerState(
                PLAYER_GAMEMODE_ACTION,
                new PlayerContainer(targetPlayer.getName(), targetPlayer.getUniqueId()),
                previousMode.name(),
                nextMode.name(),
                "gamemode"
            ),
            actor,
            targetPlayer.getWorld(),
            targetPlayer.getLocation()
        );
    }

    public static void logPlayerFlySpeed(Player actor, Player targetPlayer, float previousSpeed, float nextSpeed) {
        if (shouldSkipLogging(PrismLoggingType.PLAYER_FLY_SPEED_CHANGES) || Float.compare(previousSpeed, nextSpeed) == 0) {
            return;
        }

        record(
            new PrismAxiomActions.PlayerState(
                PLAYER_FLY_SPEED_ACTION,
                new PlayerContainer(targetPlayer.getName(), targetPlayer.getUniqueId()),
                Float.toString(previousSpeed),
                Float.toString(nextSpeed),
                "fly-speed"
            ),
            actor,
            targetPlayer.getWorld(),
            targetPlayer.getLocation()
        );
    }

    public static void logPlayerNoPhysicalTrigger(Player actor, Player targetPlayer, boolean previousValue, boolean nextValue) {
        if (shouldSkipLogging(PrismLoggingType.PLAYER_NO_PHYSICAL_TRIGGER_CHANGES) || previousValue == nextValue) {
            return;
        }

        record(
            new PrismAxiomActions.PlayerState(
                PLAYER_NO_PHYSICAL_TRIGGER_ACTION,
                new PlayerContainer(targetPlayer.getName(), targetPlayer.getUniqueId()),
                Boolean.toString(previousValue),
                Boolean.toString(nextValue),
                "no-physical-trigger"
            ),
            actor,
            targetPlayer.getWorld(),
            targetPlayer.getLocation()
        );
    }

    public static void logWorldTimeChange(
        Player actor,
        World world,
        long previousTime,
        boolean previousDaylightCycleEnabled,
        long nextTime,
        boolean nextDaylightCycleEnabled
    ) {
        if (shouldSkipLogging(PrismLoggingType.WORLD_TIME_CHANGES) ||
            (previousTime == nextTime && previousDaylightCycleEnabled == nextDaylightCycleEnabled)) {
            return;
        }

        record(
            new PrismAxiomActions.GenericState(
                WORLD_TIME_ACTION,
                "time",
                PrismAxiomSerialization.encodeWorldTimeState(previousTime, previousDaylightCycleEnabled),
                PrismAxiomSerialization.encodeWorldTimeState(nextTime, nextDaylightCycleEnabled)
            ),
            actor,
            world,
            actor.getLocation()
        );
    }

    public static void logWorldPropertyChange(Player actor, World world, String propertyId, byte[] previousValue, byte[] nextValue) {
        if (shouldSkipLogging(PrismLoggingType.WORLD_PROPERTY_CHANGES) || java.util.Arrays.equals(previousValue, nextValue)) {
            return;
        }

        record(
            new PrismAxiomActions.GenericState(
                WORLD_PROPERTY_ACTION,
                propertyId,
                PrismAxiomSerialization.encodeBytes(previousValue),
                PrismAxiomSerialization.encodeBytes(nextValue)
            ),
            actor,
            world,
            actor.getLocation()
        );
    }

    public static void logAnnotationSnapshot(Player actor, World world, byte[] previousSnapshot, byte[] nextSnapshot) {
        if (shouldSkipLogging(PrismLoggingType.ANNOTATION_SNAPSHOTS) || java.util.Arrays.equals(previousSnapshot, nextSnapshot)) {
            return;
        }

        record(
            new PrismAxiomActions.GenericState(
                ANNOTATION_SNAPSHOT_ACTION,
                "annotations",
                PrismAxiomSerialization.encodeBytes(previousSnapshot),
                PrismAxiomSerialization.encodeBytes(nextSnapshot)
            ),
            actor,
            world,
            actor.getLocation()
        );
    }

    @Nullable
    public static String captureEntitySnapshot(Entity entity) {
        return PrismAxiomSerialization.captureEntitySnapshot(entity);
    }

    private static PrismPaperApi lookupPrismApi() {
        RegisteredServiceProvider<PrismPaperApi> provider = Bukkit.getServicesManager().getRegistration(PrismPaperApi.class);
        return provider == null ? null : provider.getProvider();
    }

    @Nullable
    private static ActionType registerActionType(@Nullable ActionType actionType) {
        if (PRISM_API == null || actionType == null) {
            return null;
        }

        var existingActionType = PRISM_API.actionTypeRegistry().actionType(actionType.key());
        if (existingActionType.isPresent()) {
            existingActionType.get().setModificationHandler(actionType.modificationHandler());
            return existingActionType.get();
        }

        PRISM_API.actionTypeRegistry().registerAction(actionType);
        return actionType;
    }

    private static void record(Action action, Player actor, World world, @Nullable Location location) {
        try {
            PrismPaperApi prismApi = PRISM_API;
            if (prismApi == null) {
                return;
            }

            Location activityLocation = location != null ? location : actor.getLocation();
            if (activityLocation.getWorld() == null) {
                activityLocation.setWorld(world);
            }

            var activity = PaperActivity.builder()
                .action(action)
                .location(activityLocation)
                .cause(actor)
                .build();
            var recordingsQueue = prismApi.recordingService();
            if (recordingsQueue == null) {
                return;
            }
            recordingsQueue.addToQueue(activity);
        } catch (Exception exception) {
            com.moulberry.axiom.AxiomPaper.PLUGIN.getLogger().warning(
                "Failed to record Prism Axiom activity: " + exception.getMessage()
            );
        }
    }
}
