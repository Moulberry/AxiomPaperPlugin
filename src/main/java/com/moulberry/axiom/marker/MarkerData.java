package com.moulberry.axiom.marker;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.Field;
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
        CustomData customData = marker.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = customData == null ? new CompoundTag() : customData.copyTag();

        String name = data.getStringOr("name", "").trim();
        if (name.isEmpty()) name = null;

        Vec3 minRegion = null;
        Vec3 maxRegion = null;
        int lineArgb = 0;
        float lineThickness = 0;
        int faceArgb = 0;

        if (data.contains("min") && data.contains("max")) {
            // Try to load min/max as absolute coordinates
            ListTag min = data.getListOrEmpty("min");
            ListTag max = data.getListOrEmpty("max");

            if (min.size() == 3 && min.get(0).getId() == Tag.TAG_DOUBLE) {
                double minX = min.getDoubleOr(0, 0.0);
                double minY = min.getDoubleOr(1, 0.0);
                double minZ = min.getDoubleOr(2, 0.0);
                minRegion = new Vec3(minX, minY, minZ);
            }

            if (max.size() == 3 && max.get(0).getId() == Tag.TAG_DOUBLE) {
                double maxX = max.getDoubleOr(0, 0.0);
                double maxY = max.getDoubleOr(1, 0.0);
                double maxZ = max.getDoubleOr(2, 0.0);
                maxRegion = new Vec3(maxX, maxY, maxZ);
            }

            if (minRegion == null) {
                // Try to load min as string coordinates
                if (min.size() == 3 && min.get(0).getId() == Tag.TAG_STRING) {
                    double minX = calculateCoordinate(min.getStringOr(0, ""), position.x);
                    double minY = calculateCoordinate(min.getStringOr(1, ""), position.y);
                    double minZ = calculateCoordinate(min.getStringOr(2, ""), position.z);
                    if (Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)) {
                        minRegion = new Vec3(minX, minY, minZ);
                    }
                }
            }
            if (maxRegion == null) {
                // Try to load max as string coordinates
                if (max.size() == 3 && max.get(0).getId() == Tag.TAG_STRING) {
                    double maxX = calculateCoordinate(max.getStringOr(0, ""), position.x);
                    double maxY = calculateCoordinate(max.getStringOr(1, ""), position.y);
                    double maxZ = calculateCoordinate(max.getStringOr(2, ""), position.z);
                    if (Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)) {
                        maxRegion = new Vec3(maxX, maxY, maxZ);
                    }
                }
            }

            if (minRegion != null && maxRegion != null) {
                lineArgb = data.getIntOr("line_argb", 0);
                lineThickness = data.getIntOr("line_thickness", 0);
                faceArgb = data.getIntOr("face_argb", 0);
            }
        }

        return new MarkerData(marker.getUUID(), position, name, minRegion, maxRegion, lineArgb, lineThickness, faceArgb);
    }

    private static double calculateCoordinate(String coordinate, double position) {
        coordinate = coordinate.trim();
        boolean relative = coordinate.startsWith("~");
        if (relative) {
            coordinate = coordinate.substring(1).trim();
        }

        try {
            double value = Double.parseDouble(coordinate);
            if (relative) {
                return position + value;
            } else {
                return value;
            }
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

}
