package com.moulberry.axiom.marker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public record MarkerData(UUID uuid, Vec3 position, @Nullable String name, @Nullable Vec3 minRegion, @Nullable Vec3 maxRegion,
                         int lineArgb, float lineThickness, int faceArgb) {
    public static MarkerData read(FriendlyByteBuf friendlyByteBuf) {
        UUID uuid = friendlyByteBuf.readUUID();
        Vec3 position = new Vec3(friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble());
        String name = friendlyByteBuf.readNullable(FriendlyByteBuf::readUtf);

        Vec3 minRegion = null;
        Vec3 maxRegion = null;
        int lineArgb = 0;
        float lineThickness = 0;
        int faceArgb = 0;

        byte flags = friendlyByteBuf.readByte();

        if (flags != 0) {
            minRegion = new Vec3(friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble());
            maxRegion = new Vec3(friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble());

            if ((flags & 2) != 0) {
                lineArgb = friendlyByteBuf.readInt();
            }

            if ((flags & 4) != 0) {
                lineThickness = friendlyByteBuf.readFloat();
            }

            if ((flags & 8) != 0) {
                faceArgb = friendlyByteBuf.readInt();
            }
        }

        return new MarkerData(uuid, position, name, minRegion, maxRegion, lineArgb, lineThickness, faceArgb);
    }

    public static void write(FriendlyByteBuf friendlyByteBuf, MarkerData markerData) {
        friendlyByteBuf.writeUUID(markerData.uuid);
        friendlyByteBuf.writeDouble(markerData.position.x);
        friendlyByteBuf.writeDouble(markerData.position.y);
        friendlyByteBuf.writeDouble(markerData.position.z);
        friendlyByteBuf.writeNullable(markerData.name, FriendlyByteBuf::writeUtf);

        if (markerData.minRegion != null && markerData.maxRegion != null) {
            byte flags = 1;
            if (markerData.lineArgb != 0) flags |= 2;
            if (markerData.lineThickness != 0) flags |= 4;
            if (markerData.faceArgb != 0) flags |= 8;
            friendlyByteBuf.writeByte(flags);

            friendlyByteBuf.writeDouble(markerData.minRegion.x);
            friendlyByteBuf.writeDouble(markerData.minRegion.y);
            friendlyByteBuf.writeDouble(markerData.minRegion.z);
            friendlyByteBuf.writeDouble(markerData.maxRegion.x);
            friendlyByteBuf.writeDouble(markerData.maxRegion.y);
            friendlyByteBuf.writeDouble(markerData.maxRegion.z);

            if (markerData.lineArgb != 0) {
                friendlyByteBuf.writeInt(markerData.lineArgb);
            }

            if (markerData.lineThickness != 0) {
                friendlyByteBuf.writeFloat(markerData.lineThickness);
            }

            if (markerData.faceArgb != 0) {
                friendlyByteBuf.writeInt(markerData.faceArgb);
            }
        } else {
            friendlyByteBuf.writeByte(0);
        }
    }

    private static final Field dataField;
    static {
        ReflectionRemapper reflectionRemapper = ReflectionRemapper.forReobfMappingsInPaperJar();
        String fieldName = reflectionRemapper.remapFieldName(Marker.class, "data");

        try {
            dataField = Marker.class.getDeclaredField(fieldName);
            dataField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static CompoundTag getData(Marker marker) {
        try {
            return (CompoundTag) dataField.get(marker);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static MarkerData createFrom(Marker marker) {
        Vec3 position = marker.position();
        CompoundTag data = getData(marker);

        String name = data.getString("name").trim();
        if (name.isEmpty()) name = null;

        Vec3 minRegion = null;
        Vec3 maxRegion = null;
        int lineArgb = 0;
        float lineThickness = 0;
        int faceArgb = 0;

        if (data.contains("min", Tag.TAG_LIST) && data.contains("max", Tag.TAG_LIST)) {
            ListTag min = data.getList("min", Tag.TAG_DOUBLE);
            ListTag max = data.getList("max", Tag.TAG_DOUBLE);

            if (min.size() == 3 && max.size() == 3) {
                double minX = min.getDouble(0);
                double minY = min.getDouble(1);
                double minZ = min.getDouble(2);
                double maxX = max.getDouble(0);
                double maxY = max.getDouble(1);
                double maxZ = max.getDouble(2);
                minRegion = new Vec3(minX, minY, minZ);
                maxRegion = new Vec3(maxX, maxY, maxZ);

                if (data.contains("line_argb", Tag.TAG_ANY_NUMERIC)) {
                    lineArgb = data.getInt("line_argb");
                }

                if (data.contains("line_thickness", Tag.TAG_ANY_NUMERIC)) {
                    lineThickness = data.getInt("line_thickness");
                }

                if (data.contains("face_argb", Tag.TAG_ANY_NUMERIC)) {
                    faceArgb = data.getInt("face_argb");
                }
            }
        }

        return new MarkerData(marker.getUUID(), position, name, minRegion, maxRegion, lineArgb, lineThickness, faceArgb);
    }
}
