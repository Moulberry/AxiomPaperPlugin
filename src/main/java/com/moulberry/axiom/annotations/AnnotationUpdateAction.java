package com.moulberry.axiom.annotations;

import com.moulberry.axiom.annotations.data.AnnotationData;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public interface AnnotationUpdateAction {

    void write(FriendlyByteBuf friendlyByteBuf);

    static AnnotationUpdateAction read(FriendlyByteBuf friendlyByteBuf) {
        byte type = friendlyByteBuf.readByte();
        if (type == 0) {
            UUID uuid = friendlyByteBuf.readUUID();
            AnnotationData annotationData = AnnotationData.read(friendlyByteBuf);
            if (annotationData == null) {
                return null;
            }
            return new CreateAnnotation(uuid, annotationData);
        } else if (type == 1) {
            return new DeleteAnnotation(friendlyByteBuf.readUUID());
        } else if (type == 2) {
            return new MoveAnnotation(friendlyByteBuf.readUUID(), new Vector3f(
                friendlyByteBuf.readFloat(),
                friendlyByteBuf.readFloat(),
                friendlyByteBuf.readFloat()
            ));
        } else if (type == 3) {
            return new ClearAllAnnotations();
        } else if (type == 4) {
            return new RotateAnnotation(friendlyByteBuf.readUUID(), new Quaternionf(
                friendlyByteBuf.readFloat(),
                friendlyByteBuf.readFloat(),
                friendlyByteBuf.readFloat(),
                friendlyByteBuf.readFloat()
            ));
        } else {
            return null;
        }
    }

    record CreateAnnotation(UUID uuid, AnnotationData annotationData) implements AnnotationUpdateAction {
        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(0);
            friendlyByteBuf.writeUUID(this.uuid);
            this.annotationData.write(friendlyByteBuf);
        }
    }

    record DeleteAnnotation(UUID uuid) implements AnnotationUpdateAction {
        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(1);
            friendlyByteBuf.writeUUID(this.uuid);
        }
    }

    record MoveAnnotation(UUID uuid, Vector3f to) implements AnnotationUpdateAction {
        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(2);
            friendlyByteBuf.writeUUID(this.uuid);
            friendlyByteBuf.writeFloat(this.to.x);
            friendlyByteBuf.writeFloat(this.to.y);
            friendlyByteBuf.writeFloat(this.to.z);
        }
    }

    record ClearAllAnnotations() implements AnnotationUpdateAction {
        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(3);
        }
    }


    record RotateAnnotation(UUID uuid, Quaternionf to) implements AnnotationUpdateAction {
        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(4);
            friendlyByteBuf.writeUUID(this.uuid);
            friendlyByteBuf.writeFloat(this.to.x);
            friendlyByteBuf.writeFloat(this.to.y);
            friendlyByteBuf.writeFloat(this.to.z);
            friendlyByteBuf.writeFloat(this.to.w);
        }
    }

}
