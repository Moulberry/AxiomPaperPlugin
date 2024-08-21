package com.moulberry.axiom.annotations.data;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record TextAnnotationData(String text, Vector3f position, Quaternionf rotation, Direction direction, float fallbackYaw, float scale,
                                 int billboardMode, int colour, boolean shadow) implements AnnotationData {

    @Override
    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    @Override
    public void setRotation(Quaternionf rotation) {
        this.rotation.set(rotation);
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeByte(1);
        friendlyByteBuf.writeUtf(this.text);
        friendlyByteBuf.writeFloat(this.position.x);
        friendlyByteBuf.writeFloat(this.position.y);
        friendlyByteBuf.writeFloat(this.position.z);
        friendlyByteBuf.writeFloat(this.rotation.x);
        friendlyByteBuf.writeFloat(this.rotation.y);
        friendlyByteBuf.writeFloat(this.rotation.z);
        friendlyByteBuf.writeFloat(this.rotation.w);
        friendlyByteBuf.writeByte(this.direction.get3DDataValue());
        friendlyByteBuf.writeFloat(this.fallbackYaw);
        friendlyByteBuf.writeFloat(this.scale);
        friendlyByteBuf.writeByte(this.billboardMode);
        friendlyByteBuf.writeInt(this.colour);
        friendlyByteBuf.writeBoolean(this.shadow);
    }

    public static TextAnnotationData read(FriendlyByteBuf friendlyByteBuf) {
        String text = friendlyByteBuf.readUtf();
        float x = friendlyByteBuf.readFloat();
        float y = friendlyByteBuf.readFloat();
        float z = friendlyByteBuf.readFloat();
        float rotX = friendlyByteBuf.readFloat();
        float rotY = friendlyByteBuf.readFloat();
        float rotZ = friendlyByteBuf.readFloat();
        float rotW = friendlyByteBuf.readFloat();
        Direction direction = Direction.from3DDataValue(friendlyByteBuf.readByte());
        float fallbackYaw = friendlyByteBuf.readFloat();
        float scale = friendlyByteBuf.readFloat();
        int billboardMode = friendlyByteBuf.readByte();
        int colour = friendlyByteBuf.readInt();
        boolean shadow = friendlyByteBuf.readBoolean();
        return new TextAnnotationData(text, new Vector3f(x, y, z), new Quaternionf(rotX, rotY, rotZ, rotW), direction, fallbackYaw, scale, billboardMode, colour, shadow);
    }

}
