package com.moulberry.axiom.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class BlueprintIo {

    private static final int MAGIC = 0xAE5BB36;

    private static final IOException NOT_VALID_BLUEPRINT = new IOException("Not a valid Blueprint");
    public static BlueprintHeader readHeader(InputStream inputStream) throws IOException {
        if (inputStream.available() < 4) throw NOT_VALID_BLUEPRINT;
        DataInputStream dataInputStream = new DataInputStream(inputStream);

        int magic = dataInputStream.readInt();
        if (magic != MAGIC) throw NOT_VALID_BLUEPRINT;

        dataInputStream.readInt(); // Ignore header length
        CompoundTag headerTag = NbtIo.read(dataInputStream);
        return BlueprintHeader.load(headerTag);
    }

    public static RawBlueprint readRawBlueprint(InputStream inputStream) throws IOException {
        if (inputStream.available() < 4) throw NOT_VALID_BLUEPRINT;
        DataInputStream dataInputStream = new DataInputStream(inputStream);

        int magic = dataInputStream.readInt();
        if (magic != MAGIC) throw NOT_VALID_BLUEPRINT;

        // Header
        dataInputStream.readInt(); // Ignore header length
        CompoundTag headerTag = NbtIo.read(dataInputStream);
        BlueprintHeader header = BlueprintHeader.load(headerTag);

        // Thumbnail
        int thumbnailLength = dataInputStream.readInt();
        byte[] thumbnailBytes = dataInputStream.readNBytes(thumbnailLength);
        if (thumbnailBytes.length < thumbnailLength) throw NOT_VALID_BLUEPRINT;

        int currentDataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();

        // Block data
        dataInputStream.readInt(); // Ignore block data length
        CompoundTag blockDataTag = NbtIo.readCompressed(dataInputStream);
        int blueprintDataVersion = blockDataTag.getInt("DataVersion");
        if (blueprintDataVersion == 0) blueprintDataVersion = currentDataVersion;

        ListTag listTag = blockDataTag.getList("BlockRegion", Tag.TAG_COMPOUND);
        Long2ObjectMap<PalettedContainer<BlockState>> blockMap = readBlocks(listTag, blueprintDataVersion);

        // Block Entities
        ListTag blockEntitiesTag = blockDataTag.getList("BlockEntities", Tag.TAG_COMPOUND);
        Long2ObjectMap<CompressedBlockEntity> blockEntities = new Long2ObjectOpenHashMap<>();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Tag tag : blockEntitiesTag) {
            CompoundTag blockEntityCompound = (CompoundTag) tag;

            // Data Fix
            if (blueprintDataVersion != currentDataVersion) {
                Dynamic<Tag> dynamic = new Dynamic<>(NbtOps.INSTANCE, blockEntityCompound);
                Dynamic<Tag> output = DataFixers.getDataFixer().update(References.BLOCK_ENTITY, dynamic,
                    blueprintDataVersion, currentDataVersion);
                blockEntityCompound = (CompoundTag) output.getValue();
            }

            BlockPos blockPos = BlockEntity.getPosFromTag(blockEntityCompound);
            long pos = blockPos.asLong();

            String id = blockEntityCompound.getString("id");
            BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(new ResourceLocation(id));

            if (type != null) {
                PalettedContainer<BlockState> container = blockMap.get(BlockPos.asLong(
                    blockPos.getX() >> 4,
                    blockPos.getY() >> 4,
                    blockPos.getZ() >> 4
                ));

                BlockState blockState = container.get(blockPos.getX() & 0xF, blockPos.getY() & 0xF, blockPos.getZ() & 0xF);
                if (type.isValid(blockState)) {
                    CompoundTag newTag = blockEntityCompound.copy();
                    newTag.remove("x");
                    newTag.remove("y");
                    newTag.remove("z");
                    newTag.remove("id");
                    CompressedBlockEntity compressedBlockEntity = CompressedBlockEntity.compress(newTag, baos);
                    blockEntities.put(pos, compressedBlockEntity);
                }
            }
        }

        return new RawBlueprint(header, thumbnailBytes, blockMap, blockEntities);
    }

    public static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
        PalettedContainer.Strategy.SECTION_STATES, Blocks.STRUCTURE_VOID.defaultBlockState());

    public static Long2ObjectMap<PalettedContainer<BlockState>> readBlocks(ListTag list, int dataVersion) {
        Long2ObjectMap<PalettedContainer<BlockState>> map = new Long2ObjectOpenHashMap<>();

        for (Tag tag : list) {
            if (tag instanceof CompoundTag compoundTag) {
                int cx = compoundTag.getInt("X");
                int cy = compoundTag.getInt("Y");
                int cz = compoundTag.getInt("Z");

                CompoundTag blockStates = compoundTag.getCompound("BlockStates");
                blockStates = DFUHelper.updatePalettedContainer(blockStates, dataVersion);
                PalettedContainer<BlockState> container = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, blockStates)
                               .getOrThrow(false, err -> {});
                map.put(BlockPos.asLong(cx, cy, cz), container);
            }
        }

        return map;
    }

    public static void writeHeader(Path inPath, Path outPath, BlueprintHeader newHeader) throws IOException {
        byte[] thumbnailAndBlockBytes;
        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(inPath))) {
            if (inputStream.available() < 4) throw NOT_VALID_BLUEPRINT;
            DataInputStream dataInputStream = new DataInputStream(inputStream);

            int magic = dataInputStream.readInt();
            if (magic != MAGIC) throw NOT_VALID_BLUEPRINT;

            // Header
            int headerLength = dataInputStream.readInt(); // Ignore header length
            if (dataInputStream.skip(headerLength) < headerLength) throw NOT_VALID_BLUEPRINT;

            thumbnailAndBlockBytes = dataInputStream.readAllBytes();
        }

        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(outPath))) {
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

            dataOutputStream.writeInt(MAGIC);

            // Write header
            CompoundTag headerTag = newHeader.save(new CompoundTag());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DataOutputStream os = new DataOutputStream(baos)) {
                NbtIo.write(headerTag, os);
            }
            dataOutputStream.writeInt(baos.size());
            baos.writeTo(dataOutputStream);

            // Copy remaining bytes
            dataOutputStream.write(thumbnailAndBlockBytes);
        }
    }

    public static void writeRaw(OutputStream outputStream, RawBlueprint rawBlueprint) throws IOException {
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

        dataOutputStream.writeInt(MAGIC);

        // Write header
        CompoundTag headerTag = rawBlueprint.header().save(new CompoundTag());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream os = new DataOutputStream(baos)) {
            NbtIo.write(headerTag, os);
        }
        dataOutputStream.writeInt(baos.size());
        baos.writeTo(dataOutputStream);

        // Write thumbnail
        dataOutputStream.writeInt(rawBlueprint.thumbnail().length);
        dataOutputStream.write(rawBlueprint.thumbnail());

        // Write block data
        CompoundTag compound = new CompoundTag();
        ListTag savedBlockRegions = new ListTag();

        for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : rawBlueprint.blocks().long2ObjectEntrySet()) {
            long pos = entry.getLongKey();
            PalettedContainer<BlockState> container = entry.getValue();

            int cx = BlockPos.getX(pos);
            int cy = BlockPos.getY(pos);
            int cz = BlockPos.getZ(pos);

            CompoundTag tag = new CompoundTag();
            tag.putInt("X", cx);
            tag.putInt("Y", cy);
            tag.putInt("Z", cz);
            Tag encoded = BlueprintIo.BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, container)
                                                       .getOrThrow(false, err -> {});
            tag.put("BlockStates", encoded);
            savedBlockRegions.add(tag);
        }

        compound.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        compound.put("BlockRegion", savedBlockRegions);

        // Write Block Entities
        ListTag blockEntitiesTag = new ListTag();
        rawBlueprint.blockEntities().forEach((pos, compressedBlockEntity) -> {
            int x = BlockPos.getX(pos);
            int y = BlockPos.getY(pos);
            int z = BlockPos.getZ(pos);

            PalettedContainer<BlockState> container = rawBlueprint.blocks().get(BlockPos.asLong(x >> 4,y >> 4, z >> 4));
            BlockState blockState = container.get(x & 0xF, y & 0xF, z & 0xF);

            BlockEntityType<?> type = BlockEntityMap.get(blockState.getBlock());
            if (type == null) return;

            ResourceLocation resourceLocation = BlockEntityType.getKey(type);

            if (resourceLocation != null) {
                CompoundTag tag = compressedBlockEntity.decompress();
                tag.putInt("x", x);
                tag.putInt("y", y);
                tag.putInt("z", z);
                tag.putString("id", resourceLocation.toString());
                blockEntitiesTag.add(tag);
            }
        });
        compound.put("BlockEntities", blockEntitiesTag);

        baos.reset();
        NbtIo.writeCompressed(compound, baos);
        dataOutputStream.writeInt(baos.size());
        baos.writeTo(dataOutputStream);
    }

}
