package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.persistence.ItemStackDataType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SwitchActiveHotbarPacketListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!player.hasPermission("axiom.*")) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        int oldHotbarIndex = friendlyByteBuf.readByte();
        int activeHotbarIndex = friendlyByteBuf.readByte();

        ItemStack[] hotbarItems = new ItemStack[9];
        for (int i=0; i<9; i++) {
            hotbarItems[i] = CraftItemStack.asCraftMirror(friendlyByteBuf.readItem());
        }

        PersistentDataContainer container = player.getPersistentDataContainer();
        PersistentDataContainer containerHotbarItems = container.get(AxiomConstants.HOTBAR_DATA, PersistentDataType.TAG_CONTAINER);
        if (containerHotbarItems == null) containerHotbarItems = container.getAdapterContext().newPersistentDataContainer();

        for (int i=0; i<9; i++) {
            if (oldHotbarIndex != activeHotbarIndex) {
                int index = oldHotbarIndex*9 + i;
                ItemStack stack = player.getInventory().getItem(i);
                if (stack == null) {
                    stack = new ItemStack(Material.AIR);
                } else {
                    stack = stack.clone();
                }
                containerHotbarItems.set(new NamespacedKey("axiom", "slot_"+index), ItemStackDataType.INSTANCE, stack);
            }
            int index = activeHotbarIndex*9 + i;
            containerHotbarItems.set(new NamespacedKey("axiom", "slot_"+index), ItemStackDataType.INSTANCE, hotbarItems[i].clone());
            if (player.getGameMode() == GameMode.CREATIVE) player.getInventory().setItem(i, hotbarItems[i]);
        }

        container.set(AxiomConstants.HOTBAR_DATA, PersistentDataType.TAG_CONTAINER, containerHotbarItems);
        container.set(AxiomConstants.ACTIVE_HOTBAR_INDEX, PersistentDataType.BYTE, (byte) activeHotbarIndex);
    }

}
