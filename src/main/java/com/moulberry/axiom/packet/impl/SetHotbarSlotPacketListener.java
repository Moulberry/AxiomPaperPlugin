package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.persistence.ItemStackDataType;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SetHotbarSlotPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SetHotbarSlotPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, "axiom.player.hotbar") || this.plugin.isMismatchedDataVersion(player.getUniqueId())) {
            return;
        }

        int index = friendlyByteBuf.readByte();
        if (index < 0 || index >= 9*9) return;
        net.minecraft.world.item.ItemStack nmsStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(friendlyByteBuf);

        PersistentDataContainer container = player.getPersistentDataContainer();
        PersistentDataContainer hotbarItems = container.get(AxiomConstants.HOTBAR_DATA, PersistentDataType.TAG_CONTAINER);
        if (hotbarItems == null) hotbarItems = container.getAdapterContext().newPersistentDataContainer();
        hotbarItems.set(new NamespacedKey("axiom", "slot_"+index), ItemStackDataType.INSTANCE, CraftItemStack.asCraftMirror(nmsStack));
        container.set(AxiomConstants.HOTBAR_DATA, PersistentDataType.TAG_CONTAINER, hotbarItems);
    }

}
