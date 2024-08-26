package com.moulberry.axiom.annotations.data;

import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface AnnotationData {

    void setPosition(Vector3f position);
    void setRotation(Quaternionf rotation);
    void write(FriendlyByteBuf friendlyByteBuf);

    static AnnotationData read(FriendlyByteBuf friendlyByteBuf) {
        byte type = friendlyByteBuf.readByte();
        if (type == 0) {
            return LineAnnotationData.read(friendlyByteBuf);
        } else if (type == 1) {
            return TextAnnotationData.read(friendlyByteBuf);
        } else if (type == 2) {
            return ImageAnnotationData.read(friendlyByteBuf);
        } else if (type == 3) {
            return FreehandOutlineAnnotationData.read(friendlyByteBuf);
        } else if (type == 4) {
            return LinesOutlineAnnotationData.read(friendlyByteBuf);
        } else if (type == 5) {
            return BoxOutlineAnnotationData.read(friendlyByteBuf);
        } else {
            return null;
        }
    }

}
