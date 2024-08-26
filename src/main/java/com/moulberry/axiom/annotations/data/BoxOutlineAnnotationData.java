package com.moulberry.axiom.annotations.data;

import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record BoxOutlineAnnotationData(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int colour) implements AnnotationData {

    public BoxOutlineAnnotationData {
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
        friendlyByteBuf.writeByte(5);
        friendlyByteBuf.writeVarInt(this.fromX);
        friendlyByteBuf.writeVarInt(this.fromY);
        friendlyByteBuf.writeVarInt(this.fromZ);
        friendlyByteBuf.writeVarInt(this.toX);
        friendlyByteBuf.writeVarInt(this.toY);
        friendlyByteBuf.writeVarInt(this.toZ);
        friendlyByteBuf.writeInt(this.colour);
    }

    public static BoxOutlineAnnotationData read(FriendlyByteBuf friendlyByteBuf) {
        int fromX = friendlyByteBuf.readVarInt();
        int fromY = friendlyByteBuf.readVarInt();
        int fromZ = friendlyByteBuf.readVarInt();
        int toX = friendlyByteBuf.readVarInt();
        int toY = friendlyByteBuf.readVarInt();
        int toZ = friendlyByteBuf.readVarInt();
        int colour = friendlyByteBuf.readInt();
        return new BoxOutlineAnnotationData(fromX, fromY, fromZ, toX, toY, toZ, colour);
    }

}
