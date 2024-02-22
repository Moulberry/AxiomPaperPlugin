package com.moulberry.axiom.blueprint;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.HashMap;
import java.util.Map;

public class BlockEntityMap {

    private static final Map<Block, BlockEntityType<?>> blockBlockEntityTypeMap = new HashMap<>();
    static {
        for (BlockEntityType<?> blockEntityType : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            for (Block validBlock : blockEntityType.validBlocks) {
                blockBlockEntityTypeMap.put(validBlock, blockEntityType);
            }
        }
    }

    public static BlockEntityType<?> get(Block block) {
        return blockBlockEntityTypeMap.get(block);
    }

}
