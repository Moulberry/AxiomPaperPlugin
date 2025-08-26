package com.moulberry.axiom.paperapi.block;

import com.moulberry.axiom.VersionHelper;
import net.kyori.adventure.key.Key;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public record ImplAxiomCustomBlock(ResourceLocation id, String translationKey, List<AxiomProperty> properties, List<BlockState> blocks,
                                   @Nullable ItemStack itemStack, Set<AxiomPlacementLogic> placementLogics, boolean sendServerPickBlockIfPossible,
                                   boolean preventRightClickInteraction, boolean preventShapeUpdates, boolean automaticRotationAndMirroring,
                                   Map<BlockState, BlockState> rotateYMappings, Map<BlockState, BlockState> flipXMappings,
                                   Map<BlockState, BlockState> flipYMappings, Map<BlockState, BlockState> flipZMappings) {
    public ImplAxiomCustomBlock(Key key, String translationKey, List<AxiomProperty> properties, List<BlockData> blocks,
            @Nullable org.bukkit.inventory.ItemStack itemStack, Set<AxiomPlacementLogic> placementLogics, boolean sendServerPickBlockIfPossible,
            boolean preventRightClickInteraction, boolean preventShapeUpdates, boolean automaticRotationAndMirroring,
            Map<BlockData, BlockData> rotateYMappings, Map<BlockData, BlockData> flipXMappings,
            Map<BlockData, BlockData> flipYMappings, Map<BlockData, BlockData> flipZMappings) {
        this(convertKey(key), translationKey, properties, convertBlockDataToBlockStates(blocks),
            itemStack == null ? null : CraftItemStack.asNMSCopy(itemStack), placementLogics, sendServerPickBlockIfPossible,
            preventRightClickInteraction, preventShapeUpdates, automaticRotationAndMirroring,
            convertBlockDataToBlockStates(rotateYMappings), convertBlockDataToBlockStates(flipXMappings),
            convertBlockDataToBlockStates(flipYMappings), convertBlockDataToBlockStates(flipZMappings));
    }

    private static ResourceLocation convertKey(Key key) {
        return VersionHelper.createResourceLocation(key.namespace(), key.value());
    }

    private static List<BlockState> convertBlockDataToBlockStates(List<BlockData> blockData) {
        List<BlockState> blockStates = new ArrayList<>(blockData.size());
        for (BlockData blockDatum : blockData) {
            blockStates.add(((CraftBlockData)blockDatum).getState());
        }
        return blockStates;
    }

    private static Map<BlockState, BlockState> convertBlockDataToBlockStates(Map<BlockData, BlockData> blockData) {
        Map<BlockState, BlockState> blockStates = new HashMap<>(blockData.size());
        for (Map.Entry<BlockData, BlockData> entry : blockData.entrySet()) {
            blockStates.put(((CraftBlockData)entry.getKey()).getState(), ((CraftBlockData)entry.getValue()).getState());
        }
        return blockStates;
    }

    public void write(RegistryFriendlyByteBuf friendlyByteBuf, boolean protocolMismatch) {
        StreamEncoder<FriendlyByteBuf, BlockState> writeBlockState = protocolMismatch ?
            ImplAxiomCustomBlock::writeBlockStateString :
            ImplAxiomCustomBlock::writeBlockStateId;

        friendlyByteBuf.writeResourceLocation(this.id);
        friendlyByteBuf.writeUtf(this.translationKey);
        friendlyByteBuf.writeCollection(this.properties, ImplAxiomCustomBlock::writeProperty);
        friendlyByteBuf.writeCollection(this.blocks, writeBlockState);

        if (this.itemStack != null && !protocolMismatch) {
            friendlyByteBuf.writeBoolean(true);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(friendlyByteBuf, this.itemStack);
        } else {
            friendlyByteBuf.writeBoolean(false);
        }

        friendlyByteBuf.writeBoolean(this.sendServerPickBlockIfPossible);
        friendlyByteBuf.writeBoolean(this.preventRightClickInteraction);
        friendlyByteBuf.writeBoolean(this.preventShapeUpdates);

        friendlyByteBuf.writeVarInt(this.placementLogics.size());
        for (AxiomPlacementLogic placementLogic : this.placementLogics) {
            String id = switch (placementLogic) {
                case AXIS -> "axiom:axis";
                case FACING -> "axiom:facing";
                case FACING_OPPOSITE -> "axiom:facing_opposite";
                case FACING_CLICKED -> "axiom:facing_clicked";
                case FACING_CLICKED_OPPOSITE -> "axiom:facing_clicked_opposite";
                case WATERLOGGED -> "axiom:waterlogged";
                case HALF -> "axiom:half";
            };
            friendlyByteBuf.writeUtf(id);
        }

        friendlyByteBuf.writeBoolean(this.automaticRotationAndMirroring);
        friendlyByteBuf.writeMap(this.rotateYMappings, writeBlockState, writeBlockState);
        friendlyByteBuf.writeMap(this.flipXMappings, writeBlockState, writeBlockState);
        friendlyByteBuf.writeMap(this.flipYMappings, writeBlockState, writeBlockState);
        friendlyByteBuf.writeMap(this.flipZMappings, writeBlockState, writeBlockState);

        friendlyByteBuf.writeVarInt(0);
    }

    private static void writeBlockStateId(FriendlyByteBuf buf, BlockState blockState) {
        buf.writeById(Block.BLOCK_STATE_REGISTRY::getIdOrThrow, blockState);
    }

    private static void writeBlockStateString(FriendlyByteBuf buf, BlockState blockState) {
        buf.writeVarInt(-1);
        buf.writeUtf(BlockStateParser.serialize(blockState));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void writeProperty(FriendlyByteBuf friendlyByteBuf, AxiomProperty property) {
        switch (property) {
            case ImplAxiomProperties.NmsAxiomProperty nmsAxiomProperty -> {
                var inner = nmsAxiomProperty.property();
                if (inner == BlockStateProperties.AXIS) {
                    friendlyByteBuf.writeByte(3);
                } else if (inner == BlockStateProperties.HORIZONTAL_AXIS) {
                    friendlyByteBuf.writeByte(4);
                } else if (inner == BlockStateProperties.FACING) {
                    friendlyByteBuf.writeByte(5);
                } else if (inner == BlockStateProperties.HORIZONTAL_FACING) {
                    friendlyByteBuf.writeByte(6);
                } else if (inner == BlockStateProperties.UP) {
                    friendlyByteBuf.writeByte(7);
                } else if (inner == BlockStateProperties.DOWN) {
                    friendlyByteBuf.writeByte(8);
                } else if (inner == BlockStateProperties.NORTH) {
                    friendlyByteBuf.writeByte(9);
                } else if (inner == BlockStateProperties.EAST) {
                    friendlyByteBuf.writeByte(10);
                } else if (inner == BlockStateProperties.SOUTH) {
                    friendlyByteBuf.writeByte(11);
                } else if (inner == BlockStateProperties.WEST) {
                    friendlyByteBuf.writeByte(12);
                } else if (inner == BlockStateProperties.WATERLOGGED) {
                    friendlyByteBuf.writeByte(13);
                } else if (inner == BlockStateProperties.HALF) {
                    friendlyByteBuf.writeByte(14);
                } else if (inner == BlockStateProperties.VERTICAL_DIRECTION) {
                    friendlyByteBuf.writeByte(15);
                } else if (inner instanceof BooleanProperty) {
                    friendlyByteBuf.writeByte(0);
                    friendlyByteBuf.writeUtf(inner.getName());
                } else if (inner instanceof IntegerProperty integerProperty) {
                    friendlyByteBuf.writeByte(1);
                    friendlyByteBuf.writeUtf(inner.getName());
                    friendlyByteBuf.writeInt(integerProperty.min);
                    friendlyByteBuf.writeInt(integerProperty.max);
                } else if (inner instanceof EnumProperty enumProperty) {
                    friendlyByteBuf.writeByte(2);
                    friendlyByteBuf.writeUtf(inner.getName());
                    friendlyByteBuf.writeCollection(enumProperty.getPossibleValues(), (buf, e) -> buf.writeUtf(((StringRepresentable)e).getSerializedName()));
                } else {
                    throw new UnsupportedOperationException("Unknown property type: " + property.getClass());
                }
            }
            case ImplAxiomProperties.StringProperty stringProperty -> {
                friendlyByteBuf.writeByte(2);
                friendlyByteBuf.writeUtf(stringProperty.name());
                friendlyByteBuf.writeCollection(stringProperty.values(), FriendlyByteBuf::writeUtf);
            }
        }
    }

}
