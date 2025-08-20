package com.moulberry.axiom.paperapi.display;

import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.paperapi.block.AxiomPlacementLogic;
import com.moulberry.axiom.paperapi.block.AxiomProperty;
import com.moulberry.axiom.paperapi.block.ImplAxiomProperties;
import net.kyori.adventure.key.Key;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public record ImplAxiomCustomDisplay(ResourceLocation id, String searchKey, ItemStack itemStack,
                                     @Nullable Vector3f defaultTranslation, @Nullable Quaternionf defaultLeftRotation,
                                     @Nullable Vector3f defaultScale, @Nullable Quaternionf defaultRightRotation,
                                     int defaultBlockBrightnessOverride, int defaultSkyBrightnessOverride) {
    public ImplAxiomCustomDisplay(Key key, String searchKey, org.bukkit.inventory.ItemStack itemStack,
            @Nullable Vector3f defaultTranslation, @Nullable Quaternionf defaultLeftRotation,
            @Nullable Vector3f defaultScale, @Nullable Quaternionf defaultRightRotation,
            int defaultBlockBrightnessOverride, int defaultSkyBrightnessOverride) {
        this(convertKey(key), searchKey, CraftItemStack.asNMSCopy(itemStack),
            defaultTranslation, defaultLeftRotation, defaultScale, defaultRightRotation,
            defaultBlockBrightnessOverride, defaultSkyBrightnessOverride);
    }

    private static ResourceLocation convertKey(Key key) {
        return VersionHelper.createResourceLocation(key.namespace(), key.value());
    }

    public void write(RegistryFriendlyByteBuf friendlyByteBuf) {
        ItemStack.STREAM_CODEC.encode(friendlyByteBuf, this.itemStack);

        friendlyByteBuf.writeResourceLocation(this.id);
        friendlyByteBuf.writeUtf(this.searchKey);

        if (this.defaultTranslation != null || this.defaultLeftRotation != null || this.defaultScale != null || this.defaultRightRotation != null) {
            friendlyByteBuf.writeBoolean(true);
            friendlyByteBuf.writeVector3f(this.defaultTranslation != null ? this.defaultTranslation : new Vector3f());
            friendlyByteBuf.writeQuaternion(this.defaultLeftRotation != null ? this.defaultLeftRotation : new Quaternionf());
            friendlyByteBuf.writeVector3f(this.defaultScale != null ? this.defaultScale : new Vector3f(1f));
            friendlyByteBuf.writeQuaternion(this.defaultRightRotation != null ? this.defaultRightRotation : new Quaternionf());
        } else {
            friendlyByteBuf.writeBoolean(false);
        }

        if (this.defaultBlockBrightnessOverride >= 0 && this.defaultSkyBrightnessOverride >= 0) {
            friendlyByteBuf.writeBoolean(true);
            friendlyByteBuf.writeByte(this.defaultBlockBrightnessOverride);
            friendlyByteBuf.writeByte(this.defaultSkyBrightnessOverride);
        } else {
            friendlyByteBuf.writeBoolean(false);
        }
    }

}
