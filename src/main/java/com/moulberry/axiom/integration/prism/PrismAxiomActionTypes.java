package com.moulberry.axiom.integration.prism;

import org.prism_mc.prism.api.actions.Action;
import org.prism_mc.prism.api.actions.ActionData;
import org.prism_mc.prism.api.actions.types.ActionResultType;
import org.prism_mc.prism.api.actions.types.ActionType;
import org.prism_mc.prism.api.containers.EntityContainer;
import org.prism_mc.prism.api.containers.PlayerContainer;
import org.prism_mc.prism.api.services.modifications.ModificationHandler;
import org.prism_mc.prism.paper.api.containers.PaperEntityContainer;

import java.util.Locale;
import java.util.UUID;

final class PrismAxiomActionTypes {
    private static final EntityContainerFactory PAPER_ENTITY_CONTAINER_FACTORY = serializedEntityType -> {
        org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(
            serializedEntityType.toUpperCase(Locale.ENGLISH)
        );
        return new PaperEntityContainer(entityType);
    };

    private PrismAxiomActionTypes() {
    }

    static ActionType entitySnapshot(
        String key,
        ActionResultType resultType,
        String defaultPastTense,
        ModificationHandler modificationHandler
    ) {
        return entitySnapshot(key, resultType, defaultPastTense, modificationHandler, PAPER_ENTITY_CONTAINER_FACTORY);
    }

    static ActionType entitySnapshot(
        String key,
        ActionResultType resultType,
        String defaultPastTense,
        ModificationHandler modificationHandler,
        EntityContainerFactory entityContainerFactory
    ) {
        return new EntitySnapshotActionType(
            key,
            resultType,
            defaultPastTense,
            modificationHandler,
            entityContainerFactory
        );
    }

    static ActionType entityPassengers(String key, String defaultPastTense, ModificationHandler modificationHandler) {
        return entityPassengers(key, defaultPastTense, modificationHandler, PAPER_ENTITY_CONTAINER_FACTORY);
    }

    static ActionType entityPassengers(
        String key,
        String defaultPastTense,
        ModificationHandler modificationHandler,
        EntityContainerFactory entityContainerFactory
    ) {
        return new EntityPassengersActionType(key, defaultPastTense, modificationHandler, entityContainerFactory);
    }

    static ActionType playerState(String key, String defaultPastTense, ModificationHandler modificationHandler) {
        return new PlayerStateActionType(key, defaultPastTense, modificationHandler);
    }

    static ActionType genericState(String key, String defaultPastTense, ModificationHandler modificationHandler) {
        return new GenericStateActionType(key, defaultPastTense, modificationHandler);
    }

    static ActionType biomeState(String key, String defaultPastTense, ModificationHandler modificationHandler) {
        return new BiomeStateActionType(key, defaultPastTense, modificationHandler);
    }

    private abstract static class BaseActionType extends ActionType {
        BaseActionType(
            String key,
            ActionResultType resultType,
            String defaultPastTense,
            ModificationHandler modificationHandler
        ) {
            super(key, resultType, true, true, null, false, defaultPastTense);
            this.modificationHandler = PrismAxiomHandlers.safe(modificationHandler);
        }
    }

    private static final class EntitySnapshotActionType extends BaseActionType {
        private final EntityContainerFactory entityContainerFactory;

        private EntitySnapshotActionType(
            String key,
            ActionResultType resultType,
            String defaultPastTense,
            ModificationHandler modificationHandler,
            EntityContainerFactory entityContainerFactory
        ) {
            super(key, resultType, defaultPastTense, modificationHandler);
            this.entityContainerFactory = entityContainerFactory;
        }

        @Override
        public Action createAction(ActionData actionData) {
            if (isLookupActionData(actionData)) {
                var entityContainer = entityContainer(actionData, this.entityContainerFactory);
                return new PrismAxiomActions.LookupEntity(this, entityContainer, lookupDescriptor(actionData, entityContainer.serializeEntityType()));
            }
            String[] stateParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 3, 4);
            if (stateParts[0] == null || actionData.entityType() == null) {
                throw new IllegalArgumentException("Invalid Prism entity snapshot data");
            }
            switch (this.resultType()) {
                case CREATES -> requireEntityStates(stateParts[1] == null && stateParts[2] != null);
                case REMOVES -> requireEntityStates(stateParts[1] != null && stateParts[2] == null);
                case REPLACES -> requireEntityStates(stateParts[1] != null && stateParts[2] != null);
                default -> throw new IllegalArgumentException("Unsupported Prism entity snapshot action type");
            }
            return new PrismAxiomActions.EntitySnapshot(
                this,
                entityContainer(actionData, this.entityContainerFactory),
                UUID.fromString(stateParts[0]),
                stateParts[1],
                stateParts[2],
                stateParts.length == 4 && stateParts[3] != null ? UUID.fromString(stateParts[3]) : null,
                stateParts.length == 4
            );
        }

        private static void requireEntityStates(boolean valid) {
            if (!valid) {
                throw new IllegalArgumentException("Invalid Prism entity snapshot states");
            }
        }
    }

    private static final class PlayerStateActionType extends BaseActionType {
        private PlayerStateActionType(String key, String defaultPastTense, ModificationHandler modificationHandler) {
            super(key, ActionResultType.REPLACES, defaultPastTense, modificationHandler);
        }

        @Override
        public Action createAction(ActionData actionData) {
            if (isLookupActionData(actionData)) {
                if (actionData.affectedPlayerUuid() == null) {
                    return new PrismAxiomActions.LookupGeneric(this, lookupDescriptor(actionData, this.key()));
                }
                PlayerContainer playerContainer = playerContainer(
                    actionData.affectedPlayerName(),
                    actionData.affectedPlayerUuid()
                );
                return new PrismAxiomActions.LookupPlayer(
                    this,
                    playerContainer,
                    lookupDescriptor(actionData, playerContainer.name())
                );
            }
            String[] stateParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 4);
            PlayerContainer playerContainer;
            String previousState;
            String nextState;
            boolean hasEmbeddedIdentity = PrismAxiomSerialization.hasPartsVersion(actionData.customData())
                || stateParts[2] != null || stateParts[3] != null;
            if (hasEmbeddedIdentity) {
                if (stateParts[0] == null || stateParts[2] == null) {
                    throw new IllegalArgumentException("Invalid Prism player state data");
                }
                UUID playerUuid = UUID.fromString(stateParts[0]);
                playerContainer = playerContainer(stateParts[1], playerUuid);
                previousState = stateParts[2];
                nextState = stateParts[3];
            } else {
                // Records written by the original integration stored only the two state values.
                if (actionData.affectedPlayerUuid() == null) {
                    throw new IllegalArgumentException("Invalid Prism player state data");
                }
                playerContainer = playerContainer(
                    actionData.affectedPlayerName(),
                    actionData.affectedPlayerUuid()
                );
                previousState = stateParts[0];
                nextState = stateParts[1];
            }
            if (previousState == null || nextState == null) {
                throw new IllegalArgumentException("Invalid Prism player state data");
            }
            return new PrismAxiomActions.PlayerState(
                this,
                playerContainer,
                previousState,
                nextState
            );
        }

        private static PlayerContainer playerContainer(String playerName, UUID playerUuid) {
            String displayName = playerName == null || playerName.isBlank()
                ? playerUuid.toString()
                : playerName;
            return new PlayerContainer(displayName, playerUuid);
        }
    }

    private static final class EntityPassengersActionType extends BaseActionType {
        private final EntityContainerFactory entityContainerFactory;

        private EntityPassengersActionType(
            String key,
            String defaultPastTense,
            ModificationHandler modificationHandler,
            EntityContainerFactory entityContainerFactory
        ) {
            super(key, ActionResultType.REPLACES, defaultPastTense, modificationHandler);
            this.entityContainerFactory = entityContainerFactory;
        }

        @Override
        public Action createAction(ActionData actionData) {
            if (isLookupActionData(actionData)) {
                var entityContainer = entityContainer(actionData, this.entityContainerFactory);
                return new PrismAxiomActions.LookupEntity(this, entityContainer, lookupDescriptor(actionData, entityContainer.serializeEntityType()));
            }
            String[] stateParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 3);
            if (stateParts[0] == null || stateParts[1] == null || stateParts[2] == null
                || actionData.entityType() == null) {
                throw new IllegalArgumentException("Invalid Prism entity passenger data");
            }
            return new PrismAxiomActions.EntityPassengers(
                this,
                entityContainer(actionData, this.entityContainerFactory),
                UUID.fromString(stateParts[0]),
                stateParts[1],
                stateParts[2]
            );
        }
    }

    private static final class GenericStateActionType extends BaseActionType {
        private GenericStateActionType(String key, String defaultPastTense, ModificationHandler modificationHandler) {
            super(key, ActionResultType.REPLACES, defaultPastTense, modificationHandler);
        }

        @Override
        public Action createAction(ActionData actionData) {
            if (isLookupActionData(actionData)) {
                return new PrismAxiomActions.LookupGeneric(this, lookupDescriptor(actionData, this.key()));
            }
            String[] stateParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 2);
            if (stateParts[0] == null || stateParts[1] == null || actionData.descriptor() == null) {
                throw new IllegalArgumentException("Invalid Prism state data");
            }
            return new PrismAxiomActions.GenericState(this, actionData.descriptor(), stateParts[0], stateParts[1]);
        }
    }

    private static final class BiomeStateActionType extends BaseActionType {
        private BiomeStateActionType(String key, String defaultPastTense, ModificationHandler modificationHandler) {
            super(key, ActionResultType.REPLACES, defaultPastTense, modificationHandler);
        }

        @Override
        public Action createAction(ActionData actionData) {
            if (isLookupActionData(actionData)) {
                return new PrismAxiomActions.LookupBiome(this, lookupDescriptor(actionData, this.key()));
            }
            String[] biomeParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 2);
            if (biomeParts[0] == null || biomeParts[1] == null) {
                throw new IllegalArgumentException("Invalid Prism biome state data");
            }
            return new PrismAxiomActions.BiomeState(this, biomeParts[0], biomeParts[1]);
        }
    }

    private static boolean isLookupActionData(ActionData actionData) {
        return actionData.customDataVersion() == 0 && actionData.customData() == null;
    }

    private static String lookupDescriptor(ActionData actionData, String fallback) {
        return actionData.descriptor() == null || actionData.descriptor().isEmpty()
            ? fallback
            : actionData.descriptor();
    }

    private static EntityContainer entityContainer(
        ActionData actionData,
        EntityContainerFactory entityContainerFactory
    ) {
        if (actionData.entityType() == null) {
            throw new IllegalArgumentException("Missing Prism entity type");
        }
        return java.util.Objects.requireNonNull(
            entityContainerFactory.create(actionData.entityType()),
            "entityContainer"
        );
    }

    @FunctionalInterface
    interface EntityContainerFactory {
        EntityContainer create(String serializedEntityType);
    }
}
