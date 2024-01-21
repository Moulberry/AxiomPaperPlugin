package com.moulberry.axiom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

public class NbtSanitization {

    private static final Set<String> ALLOWED_KEYS = Set.of(
        "id", // entity id
        // generic
        "Pos",
        "Rotation",
        "Invulnerable",
        "CustomName",
        "CustomNameVisible",
        "Silent",
        "NoGravity",
        "Glowing",
        "Tags",
        "Passengers",
        // marker
        "data",
        // display entity
        "transformation",
        "interpolation_duration",
        "start_interpolation",
        "teleport_duration",
        "billboard",
        "view_range",
        "shadow_radius",
        "shadow_strength",
        "width",
        "height",
        "glow_color_override",
        "brightness",
        "line_width",
        "text_opacity",
        "background",
        "shadow",
        "see_through",
        "default_background",
        "alignment",
        "text",
        "block_state",
        "item",
        "item_display"
    );

    public static void sanitizeEntity(CompoundTag entityRoot) {
        entityRoot.getAllKeys().retainAll(ALLOWED_KEYS);

        if (entityRoot.contains("Passengers", Tag.TAG_LIST)) {
            ListTag listTag = entityRoot.getList("Passengers", Tag.TAG_COMPOUND);
            for (Tag tag : listTag) {
                sanitizeEntity((CompoundTag) tag);
            }
        }
    }

}
