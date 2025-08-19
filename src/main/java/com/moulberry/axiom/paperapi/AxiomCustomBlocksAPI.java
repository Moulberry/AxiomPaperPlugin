package com.moulberry.axiom.paperapi;

import net.kyori.adventure.key.Key;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.CheckReturnValue;

import java.util.List;

public class AxiomCustomBlocksAPI {

    private static final AxiomCustomBlocksAPI INSTANCE = new AxiomCustomBlocksAPI();

    private AxiomCustomBlocksAPI() {
    }

    public static AxiomCustomBlocksAPI getAPI() {
        return INSTANCE;
    }

    @CheckReturnValue
    public AxiomCustomBlockBuilder create(Key key, String translationKey, List<AxiomProperty> properties, List<BlockData> blocks) {
        return new AxiomCustomBlockBuilder(key, translationKey, properties, blocks);
    }

    @CheckReturnValue
    public AxiomCustomBlockBuilder createSingle(Key key, String translationKey, BlockData block) {
        return create(key, translationKey, List.of(), List.of(block));
    }

    @CheckReturnValue
    public AxiomCustomBlockBuilder createAxis(Key key, String translationKey, BlockData x, BlockData y, BlockData z) {
        AxiomCustomBlockBuilder builder = create(key, translationKey, List.of(AxiomProperty.axis()), List.of(x, y, z));
        builder.addPlacementLogic(AxiomPlacementLogic.AXIS);
        return builder;
    }

    @CheckReturnValue
    public AxiomCustomBlockBuilder createFacing(Key key, String translationKey, BlockData north, BlockData east, BlockData south, BlockData west, BlockData up, BlockData down) {
        AxiomCustomBlockBuilder builder = create(key, translationKey, List.of(AxiomProperty.facing()), List.of(north, east, south, west, up, down));
        builder.addPlacementLogic(AxiomPlacementLogic.FACING_OPPOSITE);
        return builder;
    }

    @CheckReturnValue
    public AxiomCustomBlockBuilder createHorizontalFacing(Key key, String translationKey, BlockData north, BlockData east, BlockData south, BlockData west) {
        AxiomCustomBlockBuilder builder = create(key, translationKey, List.of(AxiomProperty.horizontalFacing()), List.of(north, east, south, west));
        builder.addPlacementLogic(AxiomPlacementLogic.FACING_OPPOSITE);
        return builder;
    }

    public void register(AxiomCustomBlockBuilder customBlock) throws AxiomAlreadyRegisteredException {
        ImplServerCustomBlocks.register(customBlock.build());
    }


}
