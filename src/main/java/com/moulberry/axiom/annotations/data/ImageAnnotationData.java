package com.moulberry.axiom.annotations.data;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record ImageAnnotationData(String imageUrl, Vector3f position, Quaternionf rotation, Direction direction, float fallbackYaw, float width, float opacity, int billboardMode) implements AnnotationData {

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
        friendlyByteBuf.writeByte(2);
        friendlyByteBuf.writeUtf(this.imageUrl);
        friendlyByteBuf.writeFloat(this.position.x);
        friendlyByteBuf.writeFloat(this.position.y);
        friendlyByteBuf.writeFloat(this.position.z);
        friendlyByteBuf.writeFloat(this.rotation.x);
        friendlyByteBuf.writeFloat(this.rotation.y);
        friendlyByteBuf.writeFloat(this.rotation.z);
        friendlyByteBuf.writeFloat(this.rotation.w);
        friendlyByteBuf.writeByte(this.direction.get3DDataValue());
        friendlyByteBuf.writeFloat(this.fallbackYaw);
        friendlyByteBuf.writeFloat(this.width);
        friendlyByteBuf.writeFloat(this.opacity);
        friendlyByteBuf.writeByte(this.billboardMode);
    }

    public static ImageAnnotationData read(FriendlyByteBuf friendlyByteBuf) {
        String imageUrl = friendlyByteBuf.readUtf();
        float x = friendlyByteBuf.readFloat();
        float y = friendlyByteBuf.readFloat();
        float z = friendlyByteBuf.readFloat();
        float rotX = friendlyByteBuf.readFloat();
        float rotY = friendlyByteBuf.readFloat();
        float rotZ = friendlyByteBuf.readFloat();
        float rotW = friendlyByteBuf.readFloat();
        Direction direction = Direction.from3DDataValue(friendlyByteBuf.readByte());
        float fallbackYaw = friendlyByteBuf.readFloat();
        float width = friendlyByteBuf.readFloat();
        float opacity = friendlyByteBuf.readFloat();
        int billboardMode = friendlyByteBuf.readByte();
        return new ImageAnnotationData(imageUrl, new Vector3f(x, y, z), new Quaternionf(rotX, rotY, rotZ, rotW), direction, fallbackYaw, width, opacity, billboardMode);
    }

}
