package com.moulberry.axiom.blueprint;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

public class DFUHelper {

    private static final int DATA_VERSION = SharedConstants.getCurrentVersion().getDataVersion().getVersion();

    public static CompoundTag updatePalettedContainer(CompoundTag tag, int fromVersion) {
        if (!hasExpectedPaletteTag(tag)) {
            return tag;
        }

        if (fromVersion == DATA_VERSION) return tag;

        tag = tag.copy();

        ListTag newPalette = new ListTag();
        for (Tag entry : tag.getList("palette", Tag.TAG_COMPOUND)) {
            Dynamic<Tag> dynamic = new Dynamic<>(NbtOps.INSTANCE, entry);
            Dynamic<Tag> output = DataFixers.getDataFixer().update(References.BLOCK_STATE, dynamic, fromVersion, DATA_VERSION);
            newPalette.add(output.getValue());
        }

        tag.put("palette", newPalette);
        return tag;
    }

    private static boolean hasExpectedPaletteTag(CompoundTag tag) {
        if (!tag.contains("palette", Tag.TAG_LIST)) return false;

        ListTag listTag = (ListTag) tag.get("palette");
        if (listTag == null) return false;

        return listTag.isEmpty() || listTag.getElementType() == Tag.TAG_COMPOUND;
    }

}
