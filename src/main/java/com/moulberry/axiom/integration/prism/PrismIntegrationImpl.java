package com.moulberry.axiom.integration.prism;

import com.moulberry.axiom.AxiomPaper;
import net.kyori.adventure.text.Component;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.prism_mc.prism.api.actions.BlockAction;
import org.prism_mc.prism.api.actions.Action;
import org.prism_mc.prism.api.actions.types.ActionResultType;
import org.prism_mc.prism.api.actions.types.ActionType;
import org.prism_mc.prism.api.activities.Activity;
import org.prism_mc.prism.api.containers.BlockContainer;
import org.prism_mc.prism.api.actions.metadata.Metadata;
import org.prism_mc.prism.api.services.modifications.ModificationHandler;
import org.prism_mc.prism.api.services.modifications.ModificationQueueMode;
import org.prism_mc.prism.api.services.modifications.ModificationResult;
import org.prism_mc.prism.api.services.modifications.ModificationRuleset;
import org.prism_mc.prism.paper.api.containers.PaperBlockContainer;
import org.prism_mc.prism.paper.api.PrismPaperApi;
import org.prism_mc.prism.paper.api.activities.PaperActivity;
import org.prism_mc.prism.api.actions.ActionData;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Base64;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public class PrismIntegrationImpl {
    private static final String PLACE_ACTION_KEY = "axiom-place";
    private static final String REMOVE_ACTION_KEY = "axiom-remove";
    private static final String REPLACE_ACTION_KEY = "axiom-replace";
    private static final PrismPaperApi PRISM_API;
    private static final ActionType PLACE_ACTION;
    private static final ActionType REMOVE_ACTION;
    private static final ActionType REPLACE_ACTION;
    private static final Constructor<CraftBlockState> CRAFT_BLOCK_STATE_CONSTRUCTOR;
    private static final boolean PRISM_ENABLED;

    static {
        PRISM_API = getPrism();

        Constructor<CraftBlockState> constructor = null;
        ActionType placeAction = null;
        ActionType removeAction = null;
        ActionType replaceAction = null;
        if (PRISM_API != null) {
            try {
                constructor = CraftBlockState.class.getDeclaredConstructor(World.class, BlockPos.class, BlockState.class);
                constructor.setAccessible(true);
            } catch (NoSuchMethodException | SecurityException e) {
                AxiomPaper.PLUGIN.getLogger().warning("Failed to get CraftBlockState constructor for Prism: " + e);
            }

            if (constructor != null) {
                placeAction = registerActionType(PLACE_ACTION_KEY, ActionResultType.CREATES, new PlaceModificationHandler());
                removeAction = registerActionType(REMOVE_ACTION_KEY, ActionResultType.REMOVES, new RemoveModificationHandler());
                replaceAction = registerActionType(REPLACE_ACTION_KEY, ActionResultType.REPLACES, new ReplaceModificationHandler());
            }
        }

        CRAFT_BLOCK_STATE_CONSTRUCTOR = constructor;
        PLACE_ACTION = placeAction;
        REMOVE_ACTION = removeAction;
        REPLACE_ACTION = replaceAction;
        PRISM_ENABLED = PRISM_API != null && CRAFT_BLOCK_STATE_CONSTRUCTOR != null;
    }

    private static PrismPaperApi getPrism() {
        RegisteredServiceProvider<PrismPaperApi> provider = Bukkit.getServicesManager().getRegistration(PrismPaperApi.class);
        return provider == null ? null : provider.getProvider();
    }

    static boolean isEnabled() {
        return PRISM_ENABLED;
    }

    static void logChange(Player player, BlockState oldBlockState, @Nullable String oldBlockEntityNbt,
                          BlockState newBlockState, @Nullable String newBlockEntityNbt, CraftWorld world, BlockPos pos) {
        if (oldBlockState == newBlockState && Objects.equals(oldBlockEntityNbt, newBlockEntityNbt)) {
            return;
        }

        if (oldBlockState.isAir()) {
            recordCreate(player, world, pos, newBlockState, newBlockEntityNbt);
        } else if (newBlockState.isAir()) {
            recordRemove(player, world, pos, oldBlockState, oldBlockEntityNbt);
        } else {
            recordReplace(player, world, pos, oldBlockState, oldBlockEntityNbt, newBlockState, newBlockEntityNbt);
        }
    }

    private static CraftBlockState createCraftBlockState(World world, BlockPos pos, BlockState blockState) {
        try {
            return CRAFT_BLOCK_STATE_CONSTRUCTOR.newInstance(world, pos, blockState);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to create CraftBlockState for Prism: " + e);
            return null;
        }
    }

    private static ActionType registerActionType(String key, ActionResultType resultType, ModificationHandler handler) {
        ActionType actionType = PRISM_API.actionTypeRegistry().actionType(key)
            .orElseGet(() -> {
                ActionType created = new AxiomBlockActionType(key, resultType, handler);
                PRISM_API.actionTypeRegistry().registerAction(created);
                return created;
            });
        actionType.setModificationHandler(handler);
        return actionType;
    }

    private static void recordCreate(Player player, CraftWorld world, BlockPos pos, BlockState newBlockState, @Nullable String newBlockEntityNbt) {
        record(player, world, pos, PLACE_ACTION_KEY, () -> {
            CraftBlockState placedState = createCraftBlockState(world, pos, newBlockState);
            return placedState == null ? null :
                new AxiomBlockAction(PLACE_ACTION, new PaperBlockContainer(placedState), null, null, newBlockEntityNbt);
        });
    }

    private static void recordRemove(Player player, CraftWorld world, BlockPos pos, BlockState oldBlockState, @Nullable String oldBlockEntityNbt) {
        record(player, world, pos, REMOVE_ACTION_KEY, () -> {
            CraftBlockState removedState = createCraftBlockState(world, pos, oldBlockState);
            return removedState == null ? null :
                new AxiomBlockAction(REMOVE_ACTION, new PaperBlockContainer(removedState), null, oldBlockEntityNbt, null);
        });
    }

    private static void recordReplace(Player player, CraftWorld world, BlockPos pos, BlockState oldBlockState, @Nullable String oldBlockEntityNbt,
                                      BlockState newBlockState, @Nullable String newBlockEntityNbt) {
        record(player, world, pos, REPLACE_ACTION_KEY, () -> {
            CraftBlockState placedState = createCraftBlockState(world, pos, newBlockState);
            CraftBlockState replacedState = createCraftBlockState(world, pos, oldBlockState);
            if (placedState == null || replacedState == null) {
                return null;
            }
            return new AxiomBlockAction(REPLACE_ACTION, new PaperBlockContainer(placedState), new PaperBlockContainer(replacedState),
                oldBlockEntityNbt, newBlockEntityNbt);
        });
    }

    private static void record(Player player, CraftWorld world, BlockPos pos, String actionKey, ActionSupplier actionSupplier) {
        try {
            var action = actionSupplier.create();
            if (action == null) {
                return;
            }

            var activity = PaperActivity.builder()
                .action(action)
                .location(new Location(world, pos.getX(), pos.getY(), pos.getZ()))
                .cause(player)
                .build();

            PRISM_API.recordingService().addToQueue(activity);
        } catch (Exception e) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to record Prism activity for " + actionKey + ": " + e.getMessage());
        }
    }

    private static ModificationResult buildDefaultResult(Activity activityContext, ModificationQueueMode mode) {
        return ModificationResult.builder()
            .activity(activityContext)
            .statusFromMode(mode)
            .build();
    }

    private static ModificationResult applyBlockData(Activity activityContext, ModificationQueueMode mode,
                                                     @Nullable BlockContainer blockContainer, @Nullable String blockEntityNbt) {
        if (mode != ModificationQueueMode.COMPLETING) {
            return buildDefaultResult(activityContext, mode);
        }

        World world = Bukkit.getWorld(activityContext.worldUuid());
        if (world == null) {
            return ModificationResult.builder()
                .activity(activityContext)
                .skipped()
                .target(activityContext.world().value())
                .build();
        }

        var coordinate = activityContext.coordinate();
        int x = coordinate.intX();
        int y = coordinate.intY();
        int z = coordinate.intZ();
        world.getChunkAt(x >> 4, z >> 4);

        Block block = world.getBlockAt(x, y, z);
        if (blockContainer == null) {
            block.setType(Material.AIR, false);
        } else if (blockContainer instanceof PaperBlockContainer paperBlockContainer) {
            block.setBlockData(paperBlockContainer.blockData(), false);
        } else {
            return ModificationResult.builder()
                .activity(activityContext)
                .skipped()
                .target(world.getName() + ":" + x + "," + y + "," + z)
                .build();
        }

        applyBlockEntityNbt(world, x, y, z, blockEntityNbt);
        return buildDefaultResult(activityContext, mode);
    }

    private static void applyBlockEntityNbt(World world, int x, int y, int z, @Nullable String blockEntityNbt) {
        if (blockEntityNbt == null || blockEntityNbt.isEmpty()) {
            return;
        }

        if (!(world instanceof CraftWorld craftWorld)) {
            return;
        }

        try {
            CompoundTag tag = TagParser.parseCompoundFully(blockEntityNbt);
            BlockEntity blockEntity = craftWorld.getHandle().getBlockEntity(new BlockPos(x, y, z));
            if (blockEntity == null) {
                return;
            }

            RegistryAccess registryAccess = MinecraftServer.getServer().registryAccess();
            blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, tag));
            blockEntity.setChanged();
        } catch (Exception e) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to restore Prism block entity data at " +
                world.getName() + ":" + x + "," + y + "," + z + ": " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ActionSupplier {
        Action create();
    }

    private static final class PlaceModificationHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(ModificationRuleset modificationRuleset, Object owner, Activity activityContext, ModificationQueueMode mode) {
            return applyBlockData(activityContext, mode, null, null);
        }

        @Override
        public ModificationResult applyRestore(ModificationRuleset modificationRuleset, Object owner, Activity activityContext, ModificationQueueMode mode) {
            AxiomBlockAction action = (AxiomBlockAction) activityContext.action();
            return applyBlockData(activityContext, mode, action.blockContainer(), action.newBlockEntityNbt());
        }
    }

    private static final class RemoveModificationHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(ModificationRuleset modificationRuleset, Object owner, Activity activityContext, ModificationQueueMode mode) {
            AxiomBlockAction action = (AxiomBlockAction) activityContext.action();
            return applyBlockData(activityContext, mode, action.blockContainer(), action.oldBlockEntityNbt());
        }

        @Override
        public ModificationResult applyRestore(ModificationRuleset modificationRuleset, Object owner, Activity activityContext, ModificationQueueMode mode) {
            return applyBlockData(activityContext, mode, null, null);
        }
    }

    private static final class ReplaceModificationHandler implements ModificationHandler {
        @Override
        public ModificationResult applyRollback(ModificationRuleset modificationRuleset, Object owner, Activity activityContext, ModificationQueueMode mode) {
            AxiomBlockAction action = (AxiomBlockAction) activityContext.action();
            return applyBlockData(activityContext, mode, action.replacedBlockContainer(), action.oldBlockEntityNbt());
        }

        @Override
        public ModificationResult applyRestore(ModificationRuleset modificationRuleset, Object owner, Activity activityContext, ModificationQueueMode mode) {
            AxiomBlockAction action = (AxiomBlockAction) activityContext.action();
            return applyBlockData(activityContext, mode, action.blockContainer(), action.newBlockEntityNbt());
        }
    }

    private static final class AxiomBlockActionType extends ActionType {
        private AxiomBlockActionType(String key, ActionResultType resultType, ModificationHandler modificationHandler) {
            super(key, resultType, true);
            this.modificationHandler = modificationHandler;
        }

        @Override
        public Action createAction(ActionData actionData) {
            BlockContainer blockContainer = createContainer(actionData.blockNamespace(), actionData.blockName(),
                actionData.blockData(), actionData.translationKey());
            BlockContainer replacedBlockContainer = createContainer(actionData.replacedBlockNamespace(), actionData.replacedBlockName(),
                actionData.replacedBlockData(), actionData.replacedBlockTranslationKey());
            String[] customData = decodeCustomData(actionData.customData());
            return new AxiomBlockAction(this, blockContainer, replacedBlockContainer, customData[0], customData[1]);
        }

        @Nullable
        private BlockContainer createContainer(String namespace, String name, String data, String translationKey) {
            if (name == null || name.isEmpty()) {
                return null;
            }

            String fullData = (namespace == null || namespace.isEmpty() ? name : namespace + ":" + name) + (data == null ? "" : data);
            return new PaperBlockContainer(namespace, name, Bukkit.createBlockData(fullData), translationKey);
        }
    }

    private record AxiomBlockAction(
        ActionType type,
        @Nullable BlockContainer blockContainer,
        @Nullable BlockContainer replacedBlockContainer,
        @Nullable String oldBlockEntityNbt,
        @Nullable String newBlockEntityNbt
    ) implements Action, BlockAction {
        @Override
        public String descriptor() {
            BlockContainer descriptorContainer = this.blockContainer != null ? this.blockContainer : this.replacedBlockContainer;
            if (descriptorContainer instanceof PaperBlockContainer paperBlockContainer) {
                return paperBlockContainer.blockNamespace() + ":" + paperBlockContainer.blockName() + paperBlockContainer.serializeBlockData();
            }
            return this.type.key();
        }

        @Override
        public Component descriptorComponent() {
            return Component.text(this.descriptor());
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
            return this.type;
        }

        @Override
        public boolean hasCustomData() {
            return this.oldBlockEntityNbt != null || this.newBlockEntityNbt != null;
        }

        @Override
        public String serializeCustomData() {
            return encodeNullable(this.oldBlockEntityNbt) + ";" + encodeNullable(this.newBlockEntityNbt);
        }

        @Override
        public ModificationResult applyRollback(ModificationRuleset modificationRuleset, Object owner, Activity activityContext, ModificationQueueMode mode) {
            ModificationHandler modificationHandler = this.type.modificationHandler();
            if (modificationHandler == null) {
                return ModificationResult.builder().activity(activityContext).skipped().target(this.descriptor()).build();
            }
            return modificationHandler.applyRollback(modificationRuleset, owner, activityContext, mode);
        }

        @Override
        public ModificationResult applyRestore(ModificationRuleset modificationRuleset, Object owner, Activity activityContext, ModificationQueueMode mode) {
            ModificationHandler modificationHandler = this.type.modificationHandler();
            if (modificationHandler == null) {
                return ModificationResult.builder().activity(activityContext).skipped().target(this.descriptor()).build();
            }
            return modificationHandler.applyRestore(modificationRuleset, owner, activityContext, mode);
        }
    }

    private static String encodeNullable(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String[] decodeCustomData(@Nullable String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new String[]{null, null};
        }

        String[] split = encoded.split(";", 2);
        String oldValue = split.length > 0 ? decodeNullable(split[0]) : null;
        String newValue = split.length > 1 ? decodeNullable(split[1]) : null;
        return new String[]{oldValue, newValue};
    }

    @Nullable
    private static String decodeNullable(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        return new String(Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
    }

}
