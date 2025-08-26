package com.moulberry.axiom.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public record BlueprintHeader(int version, String name, String author, List<String> tags, float thumbnailYaw, float thumbnailPitch, boolean lockedThumbnail, int blockCount, boolean containsAir) {

    public static final int CURRENT_VERSION = 2;

    public BlockState emptyBlockState() {
        if (this.version <= 1) {
            return Blocks.STRUCTURE_VOID.defaultBlockState();
        } else {
            return Blocks.VOID_AIR.defaultBlockState();
        }
    }

    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeVarInt(CURRENT_VERSION);
        friendlyByteBuf.writeUtf(this.name);
        friendlyByteBuf.writeUtf(this.author);
        friendlyByteBuf.writeCollection(this.tags, FriendlyByteBuf::writeUtf);
        friendlyByteBuf.writeInt(this.blockCount);
        friendlyByteBuf.writeBoolean(this.containsAir);
    }

    public static BlueprintHeader read(FriendlyByteBuf friendlyByteBuf) {
        int version = friendlyByteBuf.readVarInt();
        String name = friendlyByteBuf.readUtf();
        String author = friendlyByteBuf.readUtf();
        List<String> tags = friendlyByteBuf.readList(FriendlyByteBuf::readUtf);
        int blockCount = friendlyByteBuf.readInt();
        boolean containsAir = friendlyByteBuf.readBoolean();
        return new BlueprintHeader(version, name, author, tags, 0, 0, true, blockCount, containsAir);
    }

    public static BlueprintHeader load(CompoundTag tag) {
        int version = tag.getIntOr("Version", 0);
        String name = tag.getStringOr("Name", "");
        String author = tag.getStringOr("Author", "");
        float thumbnailYaw = tag.getFloatOr("ThumbnailYaw", 135f);
        float thumbnailPitch = tag.getFloatOr("ThumbnailPitch", 30f);
        boolean lockedThumbnail = tag.getBooleanOr("LockedThumbnail", false);
        int blockCount = tag.getIntOr("BlockCount", 0);
        boolean containsAir = tag.getBooleanOr("ContainsAir", false);

        List<String> tags = new ArrayList<>();
        for (Tag string : tag.getListOrEmpty("Tags")) {
            string.asString().ifPresent(tags::add);
        }

        return new BlueprintHeader(version, name, author, tags, thumbnailYaw, thumbnailPitch, lockedThumbnail, blockCount, containsAir);
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (String string : this.tags) {
            listTag.add(StringTag.valueOf(string));
        }

        tag.putInt("Version", CURRENT_VERSION);
        tag.putString("Name", this.name);
        tag.putString("Author", this.author);
        tag.put("Tags", listTag);
        tag.putFloat("ThumbnailYaw", this.thumbnailYaw);
        tag.putFloat("ThumbnailPitch", this.thumbnailPitch);
        tag.putBoolean("LockedThumbnail", this.lockedThumbnail);
        tag.putInt("BlockCount", this.blockCount);
        tag.putBoolean("ContainsAir", this.containsAir);
        return tag;
    }

}
