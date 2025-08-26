package com.moulberry.axiom.paperapi.block;

import net.kyori.adventure.key.Key;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AxiomCustomBlockBuilder {
    private final Key key;
    private final String translationKey;
    private final List<AxiomProperty> properties;
    private final List<BlockData> blocks;
    private @Nullable ItemStack itemStack = null;
    private final Set<AxiomPlacementLogic> placementLogics = EnumSet.noneOf(AxiomPlacementLogic.class);
    private boolean sendServerPickBlockIfPossible = true;
    private boolean preventRightClickInteraction = false;
    private boolean preventShapeUpdates = false;
    private boolean automaticRotationAndMirroring = true;
    private final Map<BlockData, BlockData> rotateYMappings = new HashMap<>();
    private final Map<BlockData, BlockData> flipXMappings = new HashMap<>();
    private final Map<BlockData, BlockData> flipYMappings = new HashMap<>();
    private final Map<BlockData, BlockData> flipZMappings = new HashMap<>();

    public AxiomCustomBlockBuilder(Key key, String translationKey, List<AxiomProperty> properties, List<BlockData> blocks) {
        this.key = key;
        this.translationKey = translationKey;
        this.properties = properties;
        this.blocks = blocks;
    }

    ImplAxiomCustomBlock build() {
        return new ImplAxiomCustomBlock(this.key, this.translationKey, this.properties, this.blocks,
            this.itemStack, this.placementLogics, this.sendServerPickBlockIfPossible,
            this.preventRightClickInteraction, this.preventShapeUpdates, this.automaticRotationAndMirroring,
            this.rotateYMappings, this.flipXMappings, this.flipYMappings, this.flipZMappings);
    }

    public void pickBlockItemStack(@Nullable ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public void sendServerPickBlockIfPossible(boolean sendServerPickBlockIfPossible) {
        this.sendServerPickBlockIfPossible = sendServerPickBlockIfPossible;
    }

    public void preventRightClickInteraction(boolean preventRightClickInteraction) {
        this.preventRightClickInteraction = preventRightClickInteraction;
    }

    public void preventShapeUpdates(boolean preventShapeUpdates) {
        this.preventShapeUpdates = preventShapeUpdates;
    }

    public void addPlacementLogic(AxiomPlacementLogic placementLogic) {
        this.placementLogics.add(placementLogic);
    }

    public void automaticRotationAndMirroring(boolean automaticRotationAndMirroring) {
        this.automaticRotationAndMirroring = automaticRotationAndMirroring;
    }

    public void overrideRotateY(BlockData from, BlockData to) {
        this.rotateYMappings.put(from, to);
    }

    public void overrideFlipX(BlockData from, BlockData to) {
        this.flipXMappings.put(from, to);
    }

    public void overrideFlipY(BlockData from, BlockData to) {
        this.flipYMappings.put(from, to);
    }

    public void overrideFlipZ(BlockData from, BlockData to) {
        this.flipZMappings.put(from, to);
    }


}
