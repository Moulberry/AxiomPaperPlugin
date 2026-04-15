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

    static ActionType entitySnapshot(String key, ActionResultType resultType, ModificationHandler modificationHandler) {
        return new EntitySnapshotActionType(key, resultType, modificationHandler);
    }

    static ActionType playerState(String key, ModificationHandler modificationHandler) {
        return new PlayerStateActionType(key, modificationHandler);
    }

    static ActionType genericState(String key, ModificationHandler modificationHandler) {
        return new GenericStateActionType(key, modificationHandler);
    }

    private abstract static class BaseActionType extends ActionType {
        BaseActionType(String key, ActionResultType resultType, ModificationHandler modificationHandler) {
            super(key, resultType, true);
            this.modificationHandler = modificationHandler;
        }
    }

    private static final class EntitySnapshotActionType extends BaseActionType {
        private EntitySnapshotActionType(String key, ActionResultType resultType, ModificationHandler modificationHandler) {
            super(key, resultType, modificationHandler);
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
        private PlayerStateActionType(String key, ModificationHandler modificationHandler) {
            super(key, ActionResultType.REPLACES, modificationHandler);
        }

        @Override
        public Action createAction(ActionData actionData) {
            String[] stateParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 2);
            return new PrismAxiomActions.PlayerState(
                this,
                new PlayerContainer(actionData.affectedPlayerName(), actionData.affectedPlayerUuid()),
                stateParts[0],
                stateParts[1],
                actionData.descriptor()
            );
        }
    }

    private static final class GenericStateActionType extends BaseActionType {
        private GenericStateActionType(String key, ModificationHandler modificationHandler) {
            super(key, ActionResultType.REPLACES, modificationHandler);
        }

        @Override
        public Action createAction(ActionData actionData) {
            String[] stateParts = PrismAxiomSerialization.decodeParts(actionData.customData(), 2);
            return new PrismAxiomActions.GenericState(this, actionData.descriptor(), stateParts[0], stateParts[1]);
        }
    }
}
