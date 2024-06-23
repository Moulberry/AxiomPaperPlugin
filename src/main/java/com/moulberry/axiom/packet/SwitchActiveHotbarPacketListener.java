package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.persistence.ItemStackDataType;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SwitchActiveHotbarPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;
    public SwitchActiveHotbarPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        try {
            this.process(player, message);
        } catch (Throwable t) {
            player.kick(Component.text("Error while processing packet " + channel + ": " + t.getMessage()));
        }
    }

    private void process(Player player, byte[] message) {
        if (!this.plugin.canUseAxiom(player, "axiom.player.hotbar") || this.plugin.isMismatchedDataVersion(player.getUniqueId())) {
            return;
        }

        RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(message), ((CraftPlayer)player).getHandle().registryAccess());
        int oldHotbarIndex = friendlyByteBuf.readByte();
        int activeHotbarIndex = friendlyByteBuf.readByte();

        ItemStack[] hotbarItems = new ItemStack[9];
        for (int i=0; i<9; i++) {
            hotbarItems[i] = CraftItemStack.asCraftMirror(net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(friendlyByteBuf));
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

                if (stack.isEmpty()) {
                    containerHotbarItems.remove(new NamespacedKey("axiom", "slot_" + index));
                } else {
                    containerHotbarItems.set(new NamespacedKey("axiom", "slot_" + index), ItemStackDataType.INSTANCE, stack);
                }
            }
            int index = activeHotbarIndex*9 + i;
            ItemStack hotbarItem = hotbarItems[i].clone();
            if (hotbarItem.isEmpty()) {
                containerHotbarItems.remove(new NamespacedKey("axiom", "slot_" + index));
            } else {
                containerHotbarItems.set(new NamespacedKey("axiom", "slot_"+index), ItemStackDataType.INSTANCE, hotbarItem);
            }
            if (player.getGameMode() == GameMode.CREATIVE) player.getInventory().setItem(i, hotbarItems[i]);
        }

        container.set(AxiomConstants.HOTBAR_DATA, PersistentDataType.TAG_CONTAINER, containerHotbarItems);
        container.set(AxiomConstants.ACTIVE_HOTBAR_INDEX, PersistentDataType.BYTE, (byte) activeHotbarIndex);
    }

}
