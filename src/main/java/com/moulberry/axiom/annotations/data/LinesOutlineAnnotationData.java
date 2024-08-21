package com.moulberry.axiom.annotations.data;

import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record LinesOutlineAnnotationData(long[] positions, int colour) implements AnnotationData {

    public LinesOutlineAnnotationData {
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
        friendlyByteBuf.writeByte(4);
        friendlyByteBuf.writeVarInt(this.positions.length);
        for (long position : this.positions) {
            friendlyByteBuf.writeLong(position);
        }
        friendlyByteBuf.writeInt(this.colour);
    }

    public static LinesOutlineAnnotationData read(FriendlyByteBuf friendlyByteBuf) {
        int positionCount = friendlyByteBuf.readVarInt();
        long[] positions = new long[positionCount];
        for (int i = 0; i < positionCount; i++) {
            positions[i] = friendlyByteBuf.readLong();
        }
        int colour = friendlyByteBuf.readInt();
        return new LinesOutlineAnnotationData(positions, colour);
    }

}
