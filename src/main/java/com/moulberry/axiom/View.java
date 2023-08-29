package com.moulberry.axiom;

import com.moulberry.axiom.persistence.UUIDDataType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class View {

    public String name;
    public final UUID uuid;
    public boolean pinLevel = false;
    public boolean pinLocation = false;
    private ResourceKey<Level> level = null;
    private Vec3 position = null;
    private float yaw;
    private float pitch;

    public View(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public void write(FriendlyByteBuf byteBuf) {
        byteBuf.writeUtf(this.name, 64);
        byteBuf.writeUUID(this.uuid);

        byteBuf.writeBoolean(this.pinLevel);
        if (this.pinLevel && this.level != null) {
            byteBuf.writeBoolean(true);
            byteBuf.writeResourceKey(this.level);
        } else {
            byteBuf.writeBoolean(false);
        }

        byteBuf.writeBoolean(this.pinLocation);
        if (this.position != null) {
            byteBuf.writeBoolean(true);
            byteBuf.writeDouble(this.position.x);
            byteBuf.writeDouble(this.position.y);
            byteBuf.writeDouble(this.position.z);
            byteBuf.writeFloat(this.yaw);
            byteBuf.writeFloat(this.pitch);
        } else {
            byteBuf.writeBoolean(false);
        }
    }

    public static View read(FriendlyByteBuf byteBuf) {
        View view = new View(byteBuf.readUtf(64), byteBuf.readUUID());

        view.pinLevel = byteBuf.readBoolean();
        if (byteBuf.readBoolean()) {
            view.level = byteBuf.readResourceKey(Registries.DIMENSION);
        }

        view.pinLocation = byteBuf.readBoolean();
        if (byteBuf.readBoolean()) {
            view.position = new Vec3(byteBuf.readDouble(), byteBuf.readDouble(), byteBuf.readDouble());
            view.yaw = byteBuf.readFloat();
            view.pitch = byteBuf.readFloat();
        }

        return view;
    }

    private static final NamespacedKey NAME_KEY = new NamespacedKey("axiom", "view_name");
    private static final NamespacedKey UUID_KEY = new NamespacedKey("axiom", "view_uuid");
    private static final NamespacedKey PIN_LEVEL_KEY = new NamespacedKey("axiom", "view_pin_level");
    private static final NamespacedKey LEVEL_KEY = new NamespacedKey("axiom", "view_level");
    private static final NamespacedKey PIN_LOCATION_KEY = new NamespacedKey("axiom", "view_pin_location");
    private static final NamespacedKey X_KEY = new NamespacedKey("axiom", "view_x");
    private static final NamespacedKey Y_KEY = new NamespacedKey("axiom", "view_y");
    private static final NamespacedKey Z_KEY = new NamespacedKey("axiom", "view_z");
    private static final NamespacedKey YAW_KEY = new NamespacedKey("axiom", "view_yaw");
    private static final NamespacedKey PITCH_KEY = new NamespacedKey("axiom", "view_pitch");

    public void save(PersistentDataContainer container) {
        container.set(NAME_KEY, PersistentDataType.STRING, this.name);
        container.set(UUID_KEY, UUIDDataType.INSTANCE, this.uuid);

        container.set(PIN_LEVEL_KEY, PersistentDataType.BOOLEAN, this.pinLevel);
        if (this.pinLevel && this.level != null) {
            container.set(LEVEL_KEY, PersistentDataType.STRING, this.level.location().toString());
        }

        container.set(PIN_LOCATION_KEY, PersistentDataType.BOOLEAN, this.pinLocation);
        if (this.position != null) {
            container.set(X_KEY, PersistentDataType.DOUBLE, this.position.x);
            container.set(Y_KEY, PersistentDataType.DOUBLE, this.position.y);
            container.set(Z_KEY, PersistentDataType.DOUBLE, this.position.z);
            container.set(YAW_KEY, PersistentDataType.FLOAT, this.yaw);
            container.set(PITCH_KEY, PersistentDataType.FLOAT, this.pitch);
        }
    }

    public static View load(PersistentDataContainer tag) {
        String name = tag.get(NAME_KEY, PersistentDataType.STRING);
        UUID uuid = tag.get(UUID_KEY, UUIDDataType.INSTANCE);

        View view = new View(name, uuid);

        view.pinLevel = tag.getOrDefault(PIN_LEVEL_KEY, PersistentDataType.BOOLEAN, false);
        if (tag.has(LEVEL_KEY)) {
            String level = tag.get(LEVEL_KEY, PersistentDataType.STRING);
            view.level = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(level));
        }

        view.pinLocation = tag.getOrDefault(PIN_LOCATION_KEY, PersistentDataType.BOOLEAN, false);
        if (tag.has(X_KEY) && tag.has(Y_KEY) && tag.has(Z_KEY)) {
            double x = tag.getOrDefault(X_KEY, PersistentDataType.DOUBLE, 0.0);
            double y = tag.getOrDefault(Y_KEY, PersistentDataType.DOUBLE, 0.0);
            double z = tag.getOrDefault(Z_KEY, PersistentDataType.DOUBLE, 0.0);
            view.position = new Vec3(x, y, z);
            view.yaw = tag.getOrDefault(YAW_KEY, PersistentDataType.FLOAT, 0.0f);
            view.pitch = tag.getOrDefault(PITCH_KEY, PersistentDataType.FLOAT, 0.0f);
        }

        return view;
    }

}
