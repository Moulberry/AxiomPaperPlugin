package com.moulberry.axiom.integration.prism;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;
import org.prism_mc.prism.api.actions.Action;
import org.prism_mc.prism.api.actions.CustomData;
import org.prism_mc.prism.api.actions.EntityAction;
import org.prism_mc.prism.api.actions.PlayerAction;
import org.prism_mc.prism.api.actions.metadata.Metadata;
import org.prism_mc.prism.api.actions.types.ActionType;
import org.prism_mc.prism.api.activities.Activity;
import org.prism_mc.prism.api.containers.EntityContainer;
import org.prism_mc.prism.api.containers.PlayerContainer;
import org.prism_mc.prism.api.services.modifications.ModificationHandler;
import org.prism_mc.prism.api.services.modifications.ModificationQueueMode;
import org.prism_mc.prism.api.services.modifications.ModificationResult;
import org.prism_mc.prism.api.services.modifications.ModificationRuleset;

import java.util.UUID;

final class PrismAxiomActions {
    private PrismAxiomActions() {
    }

    abstract static class BaseAction implements Action, CustomData {
        private final ActionType actionType;
        private final String descriptor;

        BaseAction(ActionType actionType, String descriptor) {
            this.actionType = actionType;
            this.descriptor = descriptor;
        }

        @Override
        public String descriptor() {
            return this.descriptor;
        }

        @Override
        public Component descriptorComponent() {
            return Component.text(this.descriptor);
        }

        @Override
        public Metadata metadata() {
            return Metadata.builder().build();
        }

        @Override
        public String serializeMetadata() {
            return null;
        }

        @Override
        public ActionType type() {
            return this.actionType;
        }

        @Override
        public ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            ModificationHandler modificationHandler = this.actionType.modificationHandler();
            if (modificationHandler == null) {
                return ModificationResult.builder().activity(activity).skipped().target(this.descriptor).build();
            }
            return modificationHandler.applyRollback(modificationRuleset, owner, activity, mode);
        }

        @Override
        public ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            ModificationHandler modificationHandler = this.actionType.modificationHandler();
            if (modificationHandler == null) {
                return ModificationResult.builder().activity(activity).skipped().target(this.descriptor).build();
            }
            return modificationHandler.applyRestore(modificationRuleset, owner, activity, mode);
        }
    }

    static final class EntitySnapshot extends BaseAction implements EntityAction {
        private final EntityContainer entityContainer;
        private final UUID entityUuid;
        private final String previousState;
        private final String nextState;

        EntitySnapshot(
            ActionType actionType,
            EntityContainer entityContainer,
            UUID entityUuid,
            @Nullable String previousState,
            @Nullable String nextState
        ) {
            super(actionType, entityContainer.serializeEntityType());
            this.entityContainer = entityContainer;
            this.entityUuid = entityUuid;
            this.previousState = previousState;
            this.nextState = nextState;
        }

        @Override
        public EntityContainer entityContainer() {
            return this.entityContainer;
        }

        @Override
        public boolean hasCustomData() {
            return true;
        }

        @Override
        public String serializeCustomData() {
            return PrismAxiomSerialization.encodeParts(this.entityUuid.toString(), this.previousState, this.nextState);
        }

        UUID entityUuid() {
            return this.entityUuid;
        }

        String previousState() {
            return this.previousState;
        }

        String nextState() {
            return this.nextState;
        }
    }

    static final class PlayerState extends BaseAction implements PlayerAction {
        private final PlayerContainer playerContainer;
        private final String previousState;
        private final String nextState;

        PlayerState(ActionType actionType, PlayerContainer playerContainer, String previousState, String nextState, String descriptor) {
            super(actionType, descriptor);
            this.playerContainer = playerContainer;
            this.previousState = previousState;
            this.nextState = nextState;
        }

        @Override
        public PlayerContainer playerContainer() {
            return this.playerContainer;
        }

        @Override
        public boolean hasCustomData() {
            return true;
        }

        @Override
        public String serializeCustomData() {
            return PrismAxiomSerialization.encodeParts(this.previousState, this.nextState);
        }

        String previousState() {
            return this.previousState;
        }

        String nextState() {
            return this.nextState;
        }
    }

    static final class GenericState extends BaseAction {
        private final String previousState;
        private final String nextState;

        GenericState(ActionType actionType, String descriptor, String previousState, String nextState) {
            super(actionType, descriptor);
            this.previousState = previousState;
            this.nextState = nextState;
        }

        @Override
        public boolean hasCustomData() {
            return true;
        }

        @Override
        public String serializeCustomData() {
            return PrismAxiomSerialization.encodeParts(this.previousState, this.nextState);
        }

        String previousState() {
            return this.previousState;
        }

        String nextState() {
            return this.nextState;
        }
    }
}
