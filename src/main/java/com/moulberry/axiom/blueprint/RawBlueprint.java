package com.moulberry.axiom.blueprint;

import com.moulberry.axiom.buffer.CompressedBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

public record RawBlueprint(BlueprintHeader header, byte[] thumbnail, Long2ObjectMap<PalettedContainer<BlockState>> blocks,
                           Long2ObjectMap<CompressedBlockEntity> blockEntities) {

    public static void writeHeader(FriendlyByteBuf friendlyByteBuf, RawBlueprint rawBlueprint) {
        rawBlueprint.header.write(friendlyByteBuf);
        friendlyByteBuf.writeByteArray(rawBlueprint.thumbnail);
    }

    public static RawBlueprint readHeader(FriendlyByteBuf friendlyByteBuf) {
        BlueprintHeader header = BlueprintHeader.read(friendlyByteBuf);
        byte[] thumbnail = friendlyByteBuf.readByteArray();

        return new RawBlueprint(header, thumbnail, null, null);
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
    }

    public static RawBlueprint read(FriendlyByteBuf friendlyByteBuf) {
        BlueprintHeader header = BlueprintHeader.read(friendlyByteBuf);
        byte[] thumbnail = friendlyByteBuf.readByteArray();

        Long2ObjectMap<PalettedContainer<BlockState>> blocks = new Long2ObjectOpenHashMap<>();

        int chunkCount = friendlyByteBuf.readVarInt();
        for (int i = 0; i < chunkCount; i++) {
            long pos = friendlyByteBuf.readLong();

            PalettedContainer<BlockState> palettedContainer = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                Blocks.STRUCTURE_VOID.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
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

        return new RawBlueprint(header, thumbnail, blocks, blockEntities);
    }

}
