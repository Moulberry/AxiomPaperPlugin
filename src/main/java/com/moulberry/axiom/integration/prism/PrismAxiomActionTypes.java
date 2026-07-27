package com.moulberry.axiom.integration.prism;

import org.prism_mc.prism.api.actions.Action;
import org.prism_mc.prism.api.actions.ActionData;
import org.prism_mc.prism.api.actions.types.ActionResultType;
import org.prism_mc.prism.api.actions.types.ActionType;
import org.prism_mc.prism.api.containers.PlayerContainer;
import org.prism_mc.prism.api.services.modifications.ModificationHandler;
import org.prism_mc.prism.paper.api.containers.PaperEntityContainer;

import java.util.Locale;
import java.util.UUID;

final class PrismAxiomActionTypes {
    private PrismAxiomActionTypes() {
    }

    static ActionType entitySnapshot(
        String key,
        ActionResultType resultType,
        String defaultPastTense,
        ModificationHandler modificationHandler
    ) {
        return new EntitySnapshotActionType(key, resultType, defaultPastTense, modificationHandler);
    }

    static ActionType playerState(String key, String defaultPastTense, ModificationHandler modificationHandler) {
        return new PlayerStateActionType(key, defaultPastTense, modificationHandler);
    }

    static ActionType genericState(String key, String defaultPastTense, ModificationHandler modificationHandler) {
        return new GenericStateActionType(key, defaultPastTense, modificationHandler);
    }

    private abstract static class BaseActionType extends ActionType {
        BaseActionType(
            String key,
            ActionResultType resultType,
            String defaultPastTense,
            ModificationHandler modificationHandler
        ) {
            super(key, resultType, true, true, null, false, defaultPastTense);
            this.modificationHandler = modificationHandler;
        }
    }

    private static final class EntitySnapshotActionType extends BaseActionType {
        private EntitySnapshotActionType(
            String key,
            ActionResultType resultType,
            String defaultPastTense,
            ModificationHandler modificationHandler
        ) {
            super(key, resultType, defaultPastTense, modificationHandler);
        }

        @Override
        public Action createAction(ActionData actionData) {
            String[] stateParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 3);
            org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(
                actionData.entityType().toUpperCase(Locale.ENGLISH)
            );
            return new PrismAxiomActions.EntitySnapshot(
                this,
                new PaperEntityContainer(entityType),
                UUID.fromString(stateParts[0]),
                stateParts[1],
                stateParts[2]
            );
        }
    }

    private static final class PlayerStateActionType extends BaseActionType {
        private PlayerStateActionType(String key, String defaultPastTense, ModificationHandler modificationHandler) {
            super(key, ActionResultType.REPLACES, defaultPastTense, modificationHandler);
        }

        @Override
        public Action createAction(ActionData actionData) {
            String[] stateParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 4);
            PlayerContainer playerContainer;
            String previousState;
            String nextState;
            if (stateParts[3] != null) {
                playerContainer = new PlayerContainer(stateParts[1], UUID.fromString(stateParts[0]));
                previousState = stateParts[2];
                nextState = stateParts[3];
            } else {
                // Records written by the original integration stored only the two state values.
                playerContainer = new PlayerContainer(actionData.affectedPlayerName(), actionData.affectedPlayerUuid());
                previousState = stateParts[0];
                nextState = stateParts[1];
            }
            return new PrismAxiomActions.PlayerState(
                this,
                playerContainer,
                previousState,
                nextState,
                actionData.descriptor()
            );
        }
    }

    private static final class GenericStateActionType extends BaseActionType {
        private GenericStateActionType(String key, String defaultPastTense, ModificationHandler modificationHandler) {
            super(key, ActionResultType.REPLACES, defaultPastTense, modificationHandler);
        }

        @Override
        public Action createAction(ActionData actionData) {
            String[] stateParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 2);
            return new PrismAxiomActions.GenericState(this, actionData.descriptor(), stateParts[0], stateParts[1]);
        }
    }
}
