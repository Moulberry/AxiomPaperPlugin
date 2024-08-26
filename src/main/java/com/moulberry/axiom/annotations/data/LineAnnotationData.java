package com.moulberry.axiom.annotations.data;

import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record LineAnnotationData(Vec3i startQuantized, byte[] offsets, float lineWidth, int colour) implements AnnotationData {

    public LineAnnotationData {
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
        friendlyByteBuf.writeByte(0);
        friendlyByteBuf.writeVarInt(this.startQuantized.getX());
        friendlyByteBuf.writeVarInt(this.startQuantized.getY());
        friendlyByteBuf.writeVarInt(this.startQuantized.getZ());
        friendlyByteBuf.writeFloat(this.lineWidth);
        friendlyByteBuf.writeInt(this.colour);
        friendlyByteBuf.writeByteArray(this.offsets);
    }

    public static LineAnnotationData read(FriendlyByteBuf friendlyByteBuf) {
        int x = friendlyByteBuf.readVarInt();
        int y = friendlyByteBuf.readVarInt();
        int z = friendlyByteBuf.readVarInt();
        float lineWidth = friendlyByteBuf.readFloat();
        int colour = friendlyByteBuf.readInt();
        byte[] offsets = friendlyByteBuf.readByteArray();
        return new LineAnnotationData(new Vec3i(x, y, z), offsets, lineWidth, colour);
    }

}
