package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.View;
import com.moulberry.axiom.event.AxiomHandshakeEvent;
import com.moulberry.axiom.persistence.ItemStackDataType;
import com.moulberry.axiom.persistence.UUIDDataType;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

public class HelloPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;
    private final Set<UUID> activeAxiomPlayers;

    public HelloPacketListener(AxiomPaper plugin, Set<UUID> activeAxiomPlayers) {
        this.plugin = plugin;
        this.activeAxiomPlayers = activeAxiomPlayers;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!player.hasPermission("axiom.*")) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        int apiVersion = friendlyByteBuf.readVarInt();
        int dataVersion = friendlyByteBuf.readVarInt();
        friendlyByteBuf.readNbt(); // Discard

        if (dataVersion != SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            player.kick(Component.text("Axiom: Incompatible data version detected, are you using ViaVersion?"));
            return;
        }

        if (apiVersion != AxiomConstants.API_VERSION) {
            player.kick(Component.text("Unsupported Axiom API Version. Server supports " + AxiomConstants.API_VERSION +
                ", while client is " + apiVersion));
            return;
        }

        // Call handshake event
        AxiomHandshakeEvent handshakeEvent = new AxiomHandshakeEvent(player);
        Bukkit.getPluginManager().callEvent(handshakeEvent);
        if (handshakeEvent.isCancelled()) {
            return;
        }

        activeAxiomPlayers.add(player.getUniqueId());

        // Enable
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeInt(handshakeEvent.getMaxBufferSize()); // Max Buffer Size
        buf.writeBoolean(false); // No source info
        buf.writeBoolean(false); // No source settings
        buf.writeVarInt(5); // Maximum Reach
        buf.writeVarInt(16); // Max editor views
        buf.writeBoolean(true); // Editable Views

        byte[] enableBytes = new byte[buf.writerIndex()];
        buf.getBytes(0, enableBytes);
        player.sendPluginMessage(this.plugin, "axiom:enable", enableBytes);

        // Initialize Hotbars
        PersistentDataContainer container = player.getPersistentDataContainer();
        int activeHotbarIndex = container.getOrDefault(AxiomConstants.ACTIVE_HOTBAR_INDEX, PersistentDataType.BYTE, (byte) 0);
        PersistentDataContainer hotbarItems = container.get(AxiomConstants.HOTBAR_DATA, PersistentDataType.TAG_CONTAINER);
        if (hotbarItems != null) {
            buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeByte((byte) activeHotbarIndex);
            for (int i=0; i<9*9; i++) {
                // Ignore selected hotbar
                if (i / 9 == activeHotbarIndex) {
                    buf.writeItem(net.minecraft.world.item.ItemStack.EMPTY);
                } else {
                    ItemStack stack = hotbarItems.get(new NamespacedKey("axiom", "slot_"+i), ItemStackDataType.INSTANCE);
                    buf.writeItem(CraftItemStack.asNMSCopy(stack));
                }
            }

            byte[] bytes = new byte[buf.writerIndex()];
            buf.getBytes(0, bytes);
            player.sendPluginMessage(this.plugin, "axiom:initialize_hotbars", bytes);
        }

        // Initialize Views
        UUID activeView = container.get(AxiomConstants.ACTIVE_VIEW, UUIDDataType.INSTANCE);
        if (activeView != null) {
            buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUUID(activeView);

            PersistentDataContainer[] views = container.get(AxiomConstants.VIEWS, PersistentDataType.TAG_CONTAINER_ARRAY);
            buf.writeVarInt(views.length);
            for (PersistentDataContainer view : views) {
                View.load(view).write(buf);
            }

            byte[] bytes = new byte[buf.writerIndex()];
            buf.getBytes(0, bytes);
            player.sendPluginMessage(this.plugin, "axiom:set_editor_views", bytes);
        }

        // Register world properties
        World world = player.getWorld();
        ServerWorldPropertiesRegistry properties = plugin.getWorldProperties(world);

        if (properties == null) {
            player.sendPluginMessage(plugin, "axiom:register_world_properties", new byte[]{0});
        } else {
            properties.registerFor(plugin, player);
        }
    }

}
