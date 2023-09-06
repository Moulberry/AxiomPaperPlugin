package com.moulberry.axiom.buffer;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.moulberry.axiom.AxiomPaper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;

import java.io.*;
import java.util.Objects;

public record CompressedBlockEntity(int originalSize, byte compressionDict, byte[] compressed) {

    private static ZstdDictCompress zstdDictCompress = null;
    private static ZstdDictDecompress zstdDictDecompress = null;

    public static void initialize(AxiomPaper plugin) {
        try (InputStream is = Objects.requireNonNull(plugin.getResource("zstd_dictionaries/block_entities_v1.dict"))) {
            byte[] bytes = is.readAllBytes();
            zstdDictCompress = new ZstdDictCompress(bytes, Zstd.defaultCompressionLevel());
            zstdDictDecompress = new ZstdDictDecompress(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static CompressedBlockEntity compress(CompoundTag tag, ByteArrayOutputStream baos) {
        try {
            baos.reset();
            DataOutputStream dos = new DataOutputStream(baos);
            NbtIo.write(tag, dos);
            byte[] uncompressed = baos.toByteArray();
            byte[] compressed = Zstd.compress(uncompressed, zstdDictCompress);
            return new CompressedBlockEntity(uncompressed.length, (byte) 0, compressed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CompoundTag decompress() {
        if (this.compressionDict != 0) throw new UnsupportedOperationException("Unknown compression dict: " + this.compressionDict);

        try {
            byte[] nbt = Zstd.decompress(this.compressed, zstdDictDecompress, this.originalSize);
            return NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbt)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static CompressedBlockEntity read(FriendlyByteBuf friendlyByteBuf) {
        int originalSize = friendlyByteBuf.readVarInt();
        byte compressionDict = friendlyByteBuf.readByte();
        byte[] compressed = friendlyByteBuf.readByteArray();
        return new CompressedBlockEntity(originalSize, compressionDict, compressed);
    }

    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeVarInt(this.originalSize);
        friendlyByteBuf.writeByte(this.compressionDict);
        friendlyByteBuf.writeByteArray(this.compressed);
    }

}
