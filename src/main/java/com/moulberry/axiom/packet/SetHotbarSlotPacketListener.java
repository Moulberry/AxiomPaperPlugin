package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.persistence.ItemStackDataType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SetHotbarSlotPacketListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!player.hasPermission("axiom.*")) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        int index = friendlyByteBuf.readByte();
        if (index < 0 || index >= 9*9) return;
        net.minecraft.world.item.ItemStack nmsStack = friendlyByteBuf.readItem();

        PersistentDataContainer container = player.getPersistentDataContainer();
        PersistentDataContainer hotbarItems = container.get(AxiomConstants.HOTBAR_DATA, PersistentDataType.TAG_CONTAINER);
        if (hotbarItems == null) hotbarItems = container.getAdapterContext().newPersistentDataContainer();
        hotbarItems.set(new NamespacedKey("axiom", "slot_"+index), ItemStackDataType.INSTANCE, CraftItemStack.asCraftMirror(nmsStack));
        container.set(AxiomConstants.HOTBAR_DATA, PersistentDataType.TAG_CONTAINER, hotbarItems);
    }

}
