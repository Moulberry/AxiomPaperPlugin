package com.moulberry.axiom.integration.prism;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;
import org.prism_mc.prism.api.actions.Action;
import org.prism_mc.prism.api.actions.BlockAction;
import org.prism_mc.prism.api.actions.CustomData;
import org.prism_mc.prism.api.actions.EntityAction;
import org.prism_mc.prism.api.actions.PlayerAction;
import org.prism_mc.prism.api.actions.metadata.Metadata;
import org.prism_mc.prism.api.actions.types.ActionType;
import org.prism_mc.prism.api.activities.Activity;
import org.prism_mc.prism.api.containers.BlockContainer;
import org.prism_mc.prism.api.containers.EntityContainer;
import org.prism_mc.prism.api.containers.PlayerContainer;
import org.prism_mc.prism.api.services.modifications.ModificationHandler;
import org.prism_mc.prism.api.services.modifications.ModificationQueueMode;
import org.prism_mc.prism.api.services.modifications.ModificationResult;
import org.prism_mc.prism.api.services.modifications.ModificationRuleset;

import java.util.Objects;
import java.util.UUID;

final class PrismAxiomActions {
    private PrismAxiomActions() {
    }

    abstract static class BaseAction implements Action {
        private final ActionType actionType;
        private final String descriptor;

        BaseAction(ActionType actionType, String descriptor) {
            this.actionType = Objects.requireNonNull(actionType, "actionType");
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
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
                return ModificationResult.builder().activity(activity).skipped().build();
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
                return ModificationResult.builder().activity(activity).skipped().build();
            }
            return modificationHandler.applyRestore(modificationRuleset, owner, activity, mode);
        }
    }

    abstract static class ReversibleAction extends BaseAction implements CustomData {
        ReversibleAction(ActionType actionType, String descriptor) {
            super(actionType, descriptor);
        }
    }

    abstract static class LookupAction extends BaseAction {
        LookupAction(ActionType actionType, String descriptor) {
            super(actionType, descriptor);
        }

        @Override
        public final ModificationResult applyRollback(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            return ModificationResult.builder().activity(activity).skipped().build();
        }

        @Override
        public final ModificationResult applyRestore(
            ModificationRuleset modificationRuleset,
            Object owner,
            Activity activity,
            ModificationQueueMode mode
        ) {
            return ModificationResult.builder().activity(activity).skipped().build();
        }
    }

    static final class EntitySnapshot extends ReversibleAction implements EntityAction {
        private final EntityContainer entityContainer;
        private final UUID entityUuid;
        private final String previousState;
        private final String nextState;
        private final UUID externalVehicleUuid;
        private final boolean externalVehicleRecorded;

        EntitySnapshot(
            ActionType actionType,
            EntityContainer entityContainer,
            UUID entityUuid,
            @Nullable String previousState,
            @Nullable String nextState,
            @Nullable UUID externalVehicleUuid,
            boolean externalVehicleRecorded
        ) {
            super(actionType, entityContainer.serializeEntityType());
            this.entityContainer = entityContainer;
            this.entityUuid = entityUuid;
            this.previousState = previousState;
            this.nextState = nextState;
            this.externalVehicleUuid = externalVehicleUuid;
            this.externalVehicleRecorded = externalVehicleRecorded;
        }

        @Override
        public EntityContainer entityContainer() {
            return this.entityContainer;
        }

        @Override
        public Component descriptorComponent() {
            String translationKey = this.entityContainer.translationKey();
            return translationKey == null
                ? super.descriptorComponent()
                : Component.translatable(translationKey);
        }

        @Override
        public boolean hasCustomData() {
            return true;
        }

        @Override
        public String serializeCustomData() {
            return PrismAxiomSerialization.encodeParts(
                this.entityUuid.toString(),
                this.previousState,
                this.nextState,
                this.externalVehicleUuid == null ? null : this.externalVehicleUuid.toString()
            );
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

        @Nullable
        UUID externalVehicleUuid() {
            return this.externalVehicleUuid;
        }

        boolean externalVehicleRecorded() {
            return this.externalVehicleRecorded;
        }
    }

    static final class EntityPassengers extends ReversibleAction implements EntityAction {
        private final EntityContainer entityContainer;
        private final UUID entityUuid;
        private final String previousPassengers;
        private final String nextPassengers;

        EntityPassengers(
            ActionType actionType,
            EntityContainer entityContainer,
            UUID entityUuid,
            String previousPassengers,
            String nextPassengers
        ) {
            super(actionType, entityContainer.serializeEntityType());
            this.entityContainer = entityContainer;
            this.entityUuid = entityUuid;
            this.previousPassengers = previousPassengers;
            this.nextPassengers = nextPassengers;
        }

        @Override
        public EntityContainer entityContainer() {
            return this.entityContainer;
        }

        @Override
        public Component descriptorComponent() {
            String translationKey = this.entityContainer.translationKey();
            return translationKey == null
                ? super.descriptorComponent()
                : Component.translatable(translationKey);
        }

        @Override
        public boolean hasCustomData() {
            return true;
        }

        @Override
        public String serializeCustomData() {
            return PrismAxiomSerialization.encodeParts(
                this.entityUuid.toString(),
                this.previousPassengers,
                this.nextPassengers
            );
        }

        UUID entityUuid() {
            return this.entityUuid;
        }

        String previousPassengers() {
            return this.previousPassengers;
        }

        String nextPassengers() {
            return this.nextPassengers;
        }
    }

    static final class PlayerState extends ReversibleAction implements PlayerAction {
        private final PlayerContainer playerContainer;
        private final String previousState;
        private final String nextState;

        PlayerState(ActionType actionType, PlayerContainer playerContainer, String previousState, String nextState) {
            super(actionType, Objects.requireNonNull(playerContainer, "playerContainer").name());
            Objects.requireNonNull(playerContainer.uuid(), "playerContainer.uuid");
            this.playerContainer = playerContainer;
            this.previousState = Objects.requireNonNull(previousState, "previousState");
            this.nextState = Objects.requireNonNull(nextState, "nextState");
        }

        @Override
        public PlayerContainer playerContainer() {
            return this.playerContainer;
        }

        @Override
        public Component descriptorComponent() {
            return Component.text(this.playerContainer.name());
        }

        @Override
        public boolean hasCustomData() {
            return true;
        }

        @Override
        public String serializeCustomData() {
            return PrismAxiomSerialization.encodeParts(
                this.playerContainer.uuid().toString(),
                this.playerContainer.name(),
                this.previousState,
                this.nextState
            );
        }

        String previousState() {
            return this.previousState;
        }

        String nextState() {
            return this.nextState;
        }
    }

    static final class GenericState extends ReversibleAction {
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

    static final class BiomeState extends ReversibleAction {
        private final String previousBiome;
        private final String nextBiome;

        BiomeState(ActionType actionType, String previousBiome, String nextBiome) {
            super(actionType, nextBiome);
            this.previousBiome = previousBiome;
            this.nextBiome = nextBiome;
        }

        @Override
        public Component descriptorComponent() {
            var biomeId = net.minecraft.resources.Identifier.tryParse(this.descriptor());
            return biomeId == null
                ? super.descriptorComponent()
                : Component.translatable(biomeId.toLanguageKey("biome"));
        }

        @Override
        public boolean hasCustomData() {
            return true;
        }

        @Override
        public String serializeCustomData() {
            return PrismAxiomSerialization.encodeParts(this.previousBiome, this.nextBiome);
        }

        String previousBiome() {
            return this.previousBiome;
        }

        String nextBiome() {
            return this.nextBiome;
        }
    }

    static final class LookupEntity extends LookupAction implements EntityAction {
        private final EntityContainer entityContainer;

        LookupEntity(ActionType actionType, EntityContainer entityContainer, String descriptor) {
            super(actionType, descriptor);
            this.entityContainer = Objects.requireNonNull(entityContainer, "entityContainer");
        }

        @Override
        public EntityContainer entityContainer() {
            return this.entityContainer;
        }

        @Override
        public Component descriptorComponent() {
            return Component.translatable(this.entityContainer.translationKey());
        }

        @Override
        public boolean hasCustomData() {
            return false;
        }

        @Override
        public String serializeCustomData() {
            return null;
        }
    }

    static final class LookupPlayer extends LookupAction implements PlayerAction {
        private final PlayerContainer playerContainer;

        LookupPlayer(ActionType actionType, PlayerContainer playerContainer, String descriptor) {
            super(actionType, descriptor);
            this.playerContainer = Objects.requireNonNull(playerContainer, "playerContainer");
        }

        @Override
        public PlayerContainer playerContainer() {
            return this.playerContainer;
        }

        @Override
        public Component descriptorComponent() {
            return Component.text(this.playerContainer.name());
        }
    }

    static final class LookupBlock extends LookupAction implements BlockAction {
        private final BlockContainer blockContainer;
        private final BlockContainer replacedBlockContainer;

        LookupBlock(
            ActionType actionType,
            BlockContainer blockContainer,
            @Nullable BlockContainer replacedBlockContainer,
            String descriptor
        ) {
            super(actionType, descriptor);
            this.blockContainer = Objects.requireNonNull(blockContainer, "blockContainer");
            this.replacedBlockContainer = replacedBlockContainer;
        }

        @Override
        public BlockContainer blockContainer() {
            return this.blockContainer;
        }

        @Override
        public @Nullable BlockContainer replacedBlockContainer() {
            return this.replacedBlockContainer;
        }

        @Override
        public Component descriptorComponent() {
            String translationKey = this.blockContainer.translationKey();
            return translationKey == null
                ? super.descriptorComponent()
                : Component.translatable(translationKey);
        }

        @Override
        public boolean hasCustomData() {
            return false;
        }

        @Override
        public String serializeCustomData() {
            return null;
        }
    }

    record LookupBlockContainer(String blockNamespace, String blockName, @Nullable String translationKey)
        implements BlockContainer {
        @Override
        public String serializeBlockData() {
            return "";
        }
    }

    static final class LookupGeneric extends LookupAction {
        LookupGeneric(ActionType actionType, String descriptor) {
            super(actionType, descriptor);
        }
    }

    static final class LookupBiome extends LookupAction {
        LookupBiome(ActionType actionType, String descriptor) {
            super(actionType, descriptor);
        }

        @Override
        public Component descriptorComponent() {
            var biomeId = net.minecraft.resources.Identifier.tryParse(this.descriptor());
            return biomeId == null
                ? super.descriptorComponent()
                : Component.translatable(biomeId.toLanguageKey("biome"));
        }
    }
}
