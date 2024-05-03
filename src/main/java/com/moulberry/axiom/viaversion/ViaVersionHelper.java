package com.moulberry.axiom.viaversion;

import com.moulberry.axiom.buffer.BlockBuffer;
import com.viaversion.viaversion.api.data.BiMappings;
import com.viaversion.viaversion.api.data.Mappings;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ViaVersionHelper {

    public static IdMapper<BlockState> applyMappings(IdMapper<BlockState> registry, BiMappings mappings) {
        IdMapper<BlockState> newBlockRegistry = new IdMapper<>();

        // Add empty mappings for non-existent blocks
        int size = mappings.mappedSize();
        for (int i = 0; i < size; i++) {
            newBlockRegistry.addMapping(BlockBuffer.EMPTY_STATE, i);
        }

        // Map blocks
        for (int i = 0; i < registry.size(); i++) {
            BlockState blockState = registry.byId(i);

            if (blockState != null) {
                int newId = mappings.getNewId(i);
                if (newId >= 0) {
                    newBlockRegistry.addMapping(blockState, newId);
                }
            }
        }

        // Ensure block -> id is correct for the empty state
        int newEmptyStateId = mappings.getNewId(registry.getId(BlockBuffer.EMPTY_STATE));
        if (newEmptyStateId >= 0) {
            newBlockRegistry.addMapping(BlockBuffer.EMPTY_STATE, newEmptyStateId);
        }

        return newBlockRegistry;
    }

}
