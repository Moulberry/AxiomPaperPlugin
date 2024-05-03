package com.moulberry.axiom;

import com.mojang.brigadier.StringReader;
import com.mojang.datafixers.util.Either;
import com.moulberry.axiom.buffer.BlockBuffer;
import com.viaversion.viaversion.api.data.Mappings;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class DisallowedBlocks {

    public static IdMapper<BlockState> createAllowedBlockRegistry(List<String> disallowedBlocks) {
        List<Predicate<BlockState>> disallowedPredicates = new ArrayList<>();

        for (String disallowedBlock : disallowedBlocks) {
            try {
                var parsed = BlockStateParser.parseForTesting(BuiltInRegistries.BLOCK.asLookup(), new StringReader(disallowedBlock), false);

                parsed.left().ifPresent(result -> {
                    disallowedPredicates.add(blockState -> {
                        if (!blockState.is(result.blockState().getBlock())) {
                            return false;
                        } else {
                            for (Property<?> property : result.properties().keySet()) {
                                if (blockState.getValue(property) != result.blockState().getValue(property)) {
                                    return false;
                                }
                            }
                            return true;
                        }
                    });
                });

                parsed.right().ifPresent(result -> {
                    disallowedPredicates.add(blockState -> {
                        if (!blockState.is(result.tag())) {
                            return false;
                        } else {
                            for(Map.Entry<String, String> entry : result.vagueProperties().entrySet()) {
                                Property<?> property = blockState.getBlock().getStateDefinition().getProperty(entry.getKey());
                                if (property == null) {
                                    return false;
                                }

                                Comparable<?> comparable = property.getValue(entry.getValue()).orElse(null);
                                if (comparable == null) {
                                    return false;
                                }

                                if (blockState.getValue(property) != comparable) {
                                    return false;
                                }
                            }

                            return true;
                        }
                    });
                });
            } catch (Exception ignored) {}
        }

        IdMapper<BlockState> allowedBlockRegistry = new IdMapper<>();

        // Create allowedBlockRegistry
        blocks:
        for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
            for (Predicate<BlockState> disallowedPredicate : disallowedPredicates) {
                if (disallowedPredicate.test(blockState)) {
                    allowedBlockRegistry.add(BlockBuffer.EMPTY_STATE);
                    continue blocks;
                }
            }

            allowedBlockRegistry.add(blockState);
        }
        allowedBlockRegistry.addMapping(BlockBuffer.EMPTY_STATE, Block.BLOCK_STATE_REGISTRY.getId(BlockBuffer.EMPTY_STATE));
        return allowedBlockRegistry;
    }

}
