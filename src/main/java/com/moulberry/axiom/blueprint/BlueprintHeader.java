package com.moulberry.axiom.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record BlueprintHeader(String name, String author, List<String> tags, float thumbnailYaw, float thumbnailPitch, boolean lockedThumbnail, int blockCount) {

    private static final int CURRENT_VERSION = 0;

    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeUtf(this.name);
        friendlyByteBuf.writeUtf(this.author);
        friendlyByteBuf.writeCollection(this.tags, FriendlyByteBuf::writeUtf);
        friendlyByteBuf.writeInt(this.blockCount);
    }

    public static BlueprintHeader read(FriendlyByteBuf friendlyByteBuf) {
        String name = friendlyByteBuf.readUtf();
        String author = friendlyByteBuf.readUtf();
        List<String> tags = friendlyByteBuf.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 1000),
            FriendlyByteBuf::readUtf);
        int blockCount = friendlyByteBuf.readInt();
        return new BlueprintHeader(name, author, tags, 0, 0, true, blockCount);
    }

    public static BlueprintHeader load(CompoundTag tag) {
        long version = tag.getLong("Version");
        String name = tag.getString("Name");
        String author = tag.getString("Author");
        float thumbnailYaw = tag.contains("ThumbnailYaw", Tag.TAG_FLOAT) ? tag.getFloat("ThumbnailYaw") : 135;
        float thumbnailPitch = tag.contains("ThumbnailPitch", Tag.TAG_FLOAT) ? tag.getFloat("ThumbnailPitch") : 30;
        boolean lockedThumbnail = tag.getBoolean("LockedThumbnail");
        int blockCount = tag.getInt("BlockCount");

        List<String> tags = new ArrayList<>();
        for (Tag string : tag.getList("Tags", Tag.TAG_STRING)) {
            tags.add(string.getAsString());
        }

        return new BlueprintHeader(name, author, tags, thumbnailYaw, thumbnailPitch, lockedThumbnail, blockCount);
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (String string : this.tags) {
            listTag.add(StringTag.valueOf(string));
        }

        tag.putLong("Version", CURRENT_VERSION);
        tag.putString("Name", this.name);
        tag.putString("Author", this.author);
        tag.put("Tags", listTag);
        tag.putFloat("ThumbnailYaw", this.thumbnailYaw);
        tag.putFloat("ThumbnailPitch", this.thumbnailPitch);
        tag.putBoolean("LockedThumbnail", this.lockedThumbnail);
        tag.putInt("BlockCount", this.blockCount);
        return tag;
    }

}
