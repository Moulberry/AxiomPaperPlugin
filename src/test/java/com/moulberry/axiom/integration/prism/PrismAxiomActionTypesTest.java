package com.moulberry.axiom.integration.prism;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.prism_mc.prism.api.actions.Action;
import org.prism_mc.prism.api.actions.ActionData;
import org.prism_mc.prism.api.actions.BlockAction;
import org.prism_mc.prism.api.actions.CustomData;
import org.prism_mc.prism.api.actions.EntityAction;
import org.prism_mc.prism.api.actions.PlayerAction;
import org.prism_mc.prism.api.actions.types.ActionResultType;
import org.prism_mc.prism.api.actions.types.ActionType;
import org.prism_mc.prism.api.containers.EntityContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismAxiomActionTypesTest {

    @Test
    void readsLegacyTwoFieldPlayerState() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        ActionType actionType = playerActionType();
        String customData = legacyParts("SURVIVAL", "CREATIVE");

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) actionType.createAction(
            actionData(customData, "Builder", playerUuid)
        );

        assertEquals(playerUuid, action.playerContainer().uuid());
        assertEquals("Builder", action.playerContainer().name());
        assertEquals("SURVIVAL", action.previousState());
        assertEquals("CREATIVE", action.nextState());
    }

    @Test
    void readsV2FourFieldPlayerState() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        ActionType actionType = playerActionType();
        String customData = PrismAxiomSerialization.encodeParts(
            playerUuid.toString(),
            "Builder",
            "SURVIVAL",
            "CREATIVE"
        );

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) actionType.createAction(
            actionData(customData, "stale-name", UUID.randomUUID())
        );

        assertEquals(playerUuid, action.playerContainer().uuid());
        assertEquals("Builder", action.playerContainer().name());
        assertEquals("SURVIVAL", action.previousState());
        assertEquals("CREATIVE", action.nextState());
    }

    @Test
    void rejectsIncompleteV2PlayerState() {
        ActionType actionType = playerActionType();
        String customData = PrismAxiomSerialization.encodeParts(null, "Builder", "SURVIVAL", "CREATIVE");

        assertThrows(
            IllegalArgumentException.class,
            () -> actionType.createAction(actionData(customData, "Builder", UUID.randomUUID()))
        );
    }

    @Test
    void rejectsV2PlayerStateWithoutNextState() {
        ActionType actionType = playerActionType();
        String customData = PrismAxiomSerialization.encodeParts(
            UUID.randomUUID().toString(),
            "Builder",
            "SURVIVAL",
            null
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> actionType.createAction(actionData(customData, "Builder", UUID.randomUUID()))
        );
    }

    @Test
    void usesUuidAsLegacyPlayerDisplayFallback() throws Exception {
        ActionType actionType = playerActionType();
        String customData = legacyParts("SURVIVAL", "CREATIVE");
        UUID playerUuid = UUID.randomUUID();

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) actionType.createAction(
            actionData(customData, null, playerUuid)
        );

        assertEquals(playerUuid, action.playerContainer().uuid());
        assertEquals(playerUuid.toString(), action.playerContainer().name());
    }

    @Test
    void usesUuidAsVersionedPlayerDisplayFallback() throws Exception {
        ActionType actionType = playerActionType();
        UUID playerUuid = UUID.randomUUID();
        String customData = PrismAxiomSerialization.encodeParts(
            playerUuid.toString(),
            null,
            "SURVIVAL",
            "CREATIVE"
        );

        PrismAxiomActions.PlayerState action = (PrismAxiomActions.PlayerState) actionType.createAction(
            actionData(customData, null, null)
        );

        assertEquals(playerUuid, action.playerContainer().uuid());
        assertEquals(playerUuid.toString(), action.playerContainer().name());
    }

    @Test
    void rejectsLegacyPlayerStateWithoutUuid() {
        ActionType actionType = playerActionType();
        String customData = legacyParts("SURVIVAL", "CREATIVE");

        assertThrows(
            IllegalArgumentException.class,
            () -> actionType.createAction(actionData(customData, "Builder", null))
        );
    }

    @Test
    void rejectsEntitySnapshotStatesThatDoNotMatchResultType() {
        ActionType actionType = PrismAxiomActionTypes.entitySnapshot(
            "test-entity-create",
            org.prism_mc.prism.api.actions.types.ActionResultType.CREATES,
            "created",
            new PrismAxiomHandlers.EntityCreateHandler()
        );
        String customData = PrismAxiomSerialization.encodeParts(
            UUID.randomUUID().toString(),
            "unexpected previous state",
            "next state"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> actionType.createAction(entityActionData(customData, "pig"))
        );
    }

    @Test
    void readsLegacyAndVersionedExternalVehicleEntitySnapshots() throws Exception {
        ActionType actionType = PrismAxiomActionTypes.entitySnapshot(
            "test-entity-delete",
            ActionResultType.REMOVES,
            "deleted",
            new PrismAxiomHandlers.EntityDeleteHandler(),
            TestEntityContainer::new
        );
        UUID entityUuid = UUID.randomUUID();
        UUID vehicleUuid = UUID.randomUUID();

        PrismAxiomActions.EntitySnapshot legacy = (PrismAxiomActions.EntitySnapshot) actionType.createAction(
            entityActionData(PrismAxiomSerialization.encodeParts(entityUuid.toString(), "snapshot", null), "pig")
        );
        PrismAxiomActions.EntitySnapshot versioned = (PrismAxiomActions.EntitySnapshot) actionType.createAction(
            entityActionData(
                PrismAxiomSerialization.encodeParts(entityUuid.toString(), "snapshot", null, vehicleUuid.toString()),
                "pig"
            )
        );

        assertEquals(entityUuid, legacy.entityUuid());
        assertNull(legacy.externalVehicleUuid());
        assertFalse(legacy.externalVehicleRecorded());
        assertEquals(vehicleUuid, versioned.externalVehicleUuid());
        assertTrue(versioned.externalVehicleRecorded());
    }

    @Test
    void createsReadOnlyEntitySnapshotForLookupData() throws Exception {
        ActionType actionType = PrismAxiomActionTypes.entitySnapshot(
            "test-entity-create",
            ActionResultType.CREATES,
            "created",
            new PrismAxiomHandlers.EntityCreateHandler(),
            TestEntityContainer::new
        );

        Action action = actionType.createAction(lookupData("pig", "pig", null, null));

        EntityAction entityAction = assertInstanceOf(EntityAction.class, action);
        assertInstanceOf(PrismAxiomActions.LookupEntity.class, action);
        assertEquals("pig", action.descriptor());
        assertEquals("pig", entityAction.entityContainer().serializeEntityType());
        assertEquals(Component.translatable("entity.minecraft.pig"), action.descriptorComponent());
        assertFalse(((CustomData) action).hasCustomData());
    }

    @Test
    void createsReadOnlyEntityPassengersForLookupData() throws Exception {
        ActionType actionType = PrismAxiomActionTypes.entityPassengers(
            "test-entity-passengers",
            "changed passengers",
            new PrismAxiomHandlers.EntityPassengersHandler(),
            TestEntityContainer::new
        );

        Action action = actionType.createAction(lookupData("minecart", "minecart", null, null));

        assertInstanceOf(EntityAction.class, action);
        assertInstanceOf(PrismAxiomActions.LookupEntity.class, action);
        assertEquals(Component.translatable("entity.minecraft.minecart"), action.descriptorComponent());
    }

    @Test
    void createsReadOnlyPlayerStateForLookupData() throws Exception {
        UUID playerUuid = UUID.randomUUID();

        Action action = playerActionType().createAction(lookupData(null, "Builder", "Builder", playerUuid));

        PlayerAction playerAction = assertInstanceOf(PlayerAction.class, action);
        assertInstanceOf(PrismAxiomActions.LookupPlayer.class, action);
        assertEquals(playerUuid, playerAction.playerContainer().uuid());
        assertEquals("Builder", playerAction.playerContainer().name());
        assertEquals(Component.text("Builder"), action.descriptorComponent());
        assertFalse(action instanceof CustomData);
    }

    @Test
    void createsReadOnlyPlayerLookupWithUuidDisplayFallback() throws Exception {
        UUID playerUuid = UUID.randomUUID();

        Action action = playerActionType().createAction(lookupData(null, null, null, playerUuid));

        PlayerAction playerAction = assertInstanceOf(PlayerAction.class, action);
        assertInstanceOf(PrismAxiomActions.LookupPlayer.class, action);
        assertEquals(playerUuid, playerAction.playerContainer().uuid());
        assertEquals(playerUuid.toString(), playerAction.playerContainer().name());
        assertEquals(Component.text(playerUuid.toString()), action.descriptorComponent());
    }

    @Test
    void createsGenericLookupWhenLegacyPlayerIdentityIsIncomplete() throws Exception {
        Action action = playerActionType().createAction(lookupData(null, "Builder", "Builder", null));

        assertInstanceOf(PrismAxiomActions.LookupGeneric.class, action);
        assertFalse(action instanceof PlayerAction);
        assertEquals("Builder", action.descriptor());
    }

    @Test
    void createsReadOnlyGenericAndBiomeStatesForLookupData() throws Exception {
        ActionType genericType = PrismAxiomActionTypes.genericState(
            "test-generic",
            "changed",
            new PrismAxiomHandlers.WorldTimeHandler()
        );
        ActionType biomeType = PrismAxiomActionTypes.biomeState(
            "test-biome",
            "replaced biome",
            new PrismAxiomHandlers.BiomeStateHandler()
        );

        Action genericAction = genericType.createAction(lookupData(null, "daylight_cycle", null, null));
        Action biomeAction = biomeType.createAction(lookupData(null, "minecraft:plains", null, null));

        assertInstanceOf(PrismAxiomActions.LookupGeneric.class, genericAction);
        assertEquals(Component.text("daylight_cycle"), genericAction.descriptorComponent());
        assertInstanceOf(PrismAxiomActions.LookupBiome.class, biomeAction);
        assertEquals(Component.translatable("biome.minecraft.plains"), biomeAction.descriptorComponent());
    }

    @Test
    void createsReadOnlyBlockForLookupDataWithoutParsingBlockData() throws Exception {
        ActionType actionType = new PrismIntegrationImpl.AxiomBlockActionType(
            "test-block-place",
            ActionResultType.CREATES,
            "placed",
            null
        );
        ActionData actionData = new ActionData(
            null,
            (short) 0,
            null,
            "minecraft",
            "stone",
            null,
            null,
            null,
            null,
            null,
            null,
            "minecraft:stone",
            null,
            (short) 0,
            "block.minecraft.stone",
            null,
            null,
            null
        );

        Action action = actionType.createAction(actionData);

        BlockAction blockAction = assertInstanceOf(BlockAction.class, action);
        assertInstanceOf(PrismAxiomActions.LookupBlock.class, action);
        assertEquals("minecraft", blockAction.blockContainer().blockNamespace());
        assertEquals("stone", blockAction.blockContainer().blockName());
        assertEquals(Component.translatable("block.minecraft.stone"), action.descriptorComponent());
        assertFalse(((CustomData) action).hasCustomData());
    }

    @Test
    void enforcesPrismDatabaseActionKeyLimitForNewRecords() {
        PrismActionKey.validateWritableKey("axiom-no-physical-trigger");
        assertEquals(25, "axiom-no-physical-trigger".length());

        assertThrows(
            IllegalArgumentException.class,
            () -> PrismActionKey.validateWritableKey("axiom-player-no-physical-trigger")
        );
    }

    @Test
    void permitsSyntacticallyValidLegacyRegistryAlias() {
        PrismActionKey.validateRegistryKey("axiom-player-no-physical-trigger");
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismActionKey.validateRegistryKey("Axiom_Invalid")
        );
    }

    private static ActionType playerActionType() {
        return PrismAxiomActionTypes.playerState(
            "test-player-state",
            "changed state",
            new PrismAxiomHandlers.PlayerGamemodeHandler()
        );
    }

    private static String legacyParts(String... values) {
        return java.util.Arrays.stream(values)
            .map(value -> Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
            .collect(java.util.stream.Collectors.joining(";"));
    }

    private static ActionData actionData(String customData, String playerName, UUID playerUuid) {
        return new ActionData(
            null,
            (short) 0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            customData,
            playerName,
            null,
            (short) 1,
            null,
            null,
            playerName,
            playerUuid
        );
    }

    private static ActionData entityActionData(String customData, String entityType) {
        return new ActionData(
            null,
            (short) 0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            entityType,
            customData,
            entityType,
            null,
            (short) 1,
            null,
            null,
            null,
            null
        );
    }

    private static ActionData lookupData(String entityType, String descriptor, String playerName, UUID playerUuid) {
        return new ActionData(
            null,
            (short) 0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            entityType,
            null,
            descriptor,
            null,
            (short) 0,
            null,
            null,
            playerName,
            playerUuid
        );
    }

    private record TestEntityContainer(String serializeEntityType) implements EntityContainer {
        @Override
        public String translationKey() {
            return "entity.minecraft." + this.serializeEntityType;
        }
    }
}
