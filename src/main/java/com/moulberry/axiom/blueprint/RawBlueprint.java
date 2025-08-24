package com.moulberry.axiom.blueprint;

import com.moulberry.axiom.buffer.BlockBuffer;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.ArrayList;
import java.util.List;

public record RawBlueprint(BlueprintHeader header, byte[] thumbnail, Long2ObjectMap<PalettedContainer<BlockState>> blocks,
                           Long2ObjectMap<CompressedBlockEntity> blockEntities, List<CompoundTag> entities) {

    public static void writeHeader(FriendlyByteBuf friendlyByteBuf, RawBlueprint rawBlueprint) {
        rawBlueprint.header.write(friendlyByteBuf);
        friendlyByteBuf.writeByteArray(rawBlueprint.thumbnail);
    }

    public static RawBlueprint readHeader(FriendlyByteBuf friendlyByteBuf) {
        BlueprintHeader header = BlueprintHeader.read(friendlyByteBuf);
        byte[] thumbnail = friendlyByteBuf.readByteArray();

        return new RawBlueprint(header, thumbnail, null, null, null);
    }

    public static void write(FriendlyByteBuf friendlyByteBuf, RawBlueprint rawBlueprint) {
        rawBlueprint.header.write(friendlyByteBuf);
        friendlyByteBuf.writeByteArray(rawBlueprint.thumbnail);

        LongSet chunkKeys = rawBlueprint.blocks.keySet();
        friendlyByteBuf.writeVarInt(chunkKeys.size());

        LongIterator longIterator = chunkKeys.longIterator();
        while (longIterator.hasNext()) {
            long pos = longIterator.nextLong();
            friendlyByteBuf.writeLong(pos);
            rawBlueprint.blocks.get(pos).write(friendlyByteBuf);
        }

        LongSet blockEntityKeys = rawBlueprint.blockEntities.keySet();
        friendlyByteBuf.writeVarInt(blockEntityKeys.size());

        longIterator = blockEntityKeys.longIterator();
        while (longIterator.hasNext()) {
            long pos = longIterator.nextLong();
            friendlyByteBuf.writeLong(pos);
            rawBlueprint.blockEntities.get(pos).write(friendlyByteBuf);
        }

        friendlyByteBuf.writeVarInt(rawBlueprint.entities.size());
        for (CompoundTag entity : rawBlueprint.entities) {
            friendlyByteBuf.writeNbt(entity);
        }
    }

    public static RawBlueprint read(FriendlyByteBuf friendlyByteBuf) {
        BlueprintHeader header = BlueprintHeader.read(friendlyByteBuf);
        byte[] thumbnail = friendlyByteBuf.readByteArray();

        Long2ObjectMap<PalettedContainer<BlockState>> blocks = new Long2ObjectOpenHashMap<>();

        BlockState emptyBlockState = header.emptyBlockState();

        int chunkCount = friendlyByteBuf.readVarInt();
        for (int i = 0; i < chunkCount; i++) {
            long pos = friendlyByteBuf.readLong();

            PalettedContainer<BlockState> palettedContainer = BlockBuffer.createPalettedContainerForEmptyBlockState(emptyBlockState);
            palettedContainer.read(friendlyByteBuf);

            blocks.put(pos, palettedContainer);
        }

        Long2ObjectMap<CompressedBlockEntity> blockEntities = new Long2ObjectOpenHashMap<>();

        int blockEntityCount = friendlyByteBuf.readVarInt();
        for (int i = 0; i < blockEntityCount; i++) {
            long pos = friendlyByteBuf.readLong();

            CompressedBlockEntity compressedBlockEntity = CompressedBlockEntity.read(friendlyByteBuf);
            blockEntities.put(pos, compressedBlockEntity);
        }

        List<CompoundTag> entities = new ArrayList<>();

        int entityCount = friendlyByteBuf.readVarInt();
        for (int i = 0; i < entityCount; i++) {
            entities.add(friendlyByteBuf.readNbt());
        }

        return new RawBlueprint(header, thumbnail, blocks, blockEntities, entities);
    }

}
