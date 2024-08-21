package com.moulberry.axiom.annotations.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record FreehandOutlineAnnotationData(BlockPos start, byte[] offsets, int offsetCount, int colour) implements AnnotationData {

    public FreehandOutlineAnnotationData {
        colour = 0xFF000000 | colour;
    }

    @Override
    public void setPosition(Vector3f position) {
    }

    @Override
    public void setRotation(Quaternionf rotation) {
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeByte(3);
        friendlyByteBuf.writeVarInt(this.start.getX());
        friendlyByteBuf.writeVarInt(this.start.getY());
        friendlyByteBuf.writeVarInt(this.start.getZ());
        friendlyByteBuf.writeVarInt(this.offsetCount);
        friendlyByteBuf.writeInt(this.colour);
        friendlyByteBuf.writeByteArray(this.offsets);
    }

    public static FreehandOutlineAnnotationData read(FriendlyByteBuf friendlyByteBuf) {
        int x = friendlyByteBuf.readVarInt();
        int y = friendlyByteBuf.readVarInt();
        int z = friendlyByteBuf.readVarInt();
        int offsetCount = friendlyByteBuf.readVarInt();
        int colour = friendlyByteBuf.readInt();
        byte[] offsets = friendlyByteBuf.readByteArray();
        return new FreehandOutlineAnnotationData(new BlockPos(x, y, z), offsets, offsetCount, colour);
    }

}
