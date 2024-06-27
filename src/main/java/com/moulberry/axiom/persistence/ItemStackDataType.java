package com.moulberry.axiom.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class ItemStackDataType implements PersistentDataType<PersistentDataContainer, ItemStack> {
    public static ItemStackDataType INSTANCE = new ItemStackDataType();
    private ItemStackDataType() {
    }

    @Override
    public @NotNull Class<PersistentDataContainer> getPrimitiveType() {
        return PersistentDataContainer.class;
    }

    @Override
    public @NotNull Class<ItemStack> getComplexType() {
        return ItemStack.class;
    }

    @Override
    public @NotNull PersistentDataContainer toPrimitive(@NotNull ItemStack complex, @NotNull PersistentDataAdapterContext context) {
        net.minecraft.world.item.ItemStack nmsStack = CraftItemStack.asNMSCopy(complex);
        if (nmsStack == null) nmsStack = net.minecraft.world.item.ItemStack.EMPTY;
        CompoundTag tag = (CompoundTag) nmsStack.saveOptional(MinecraftServer.getServer().registryAccess());

        PersistentDataContainer container = context.newPersistentDataContainer();
        ((CraftPersistentDataContainer)container).putAll(tag);
        return container;
    }

    @Override
    public @NotNull ItemStack fromPrimitive(@NotNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
        CompoundTag tag = ((CraftPersistentDataContainer)primitive).toTagCompound();
        net.minecraft.world.item.ItemStack nmsStack = net.minecraft.world.item.ItemStack.parseOptional(MinecraftServer.getServer().registryAccess(), tag);

        return CraftItemStack.asCraftMirror(nmsStack);
    }
}
