package com.moulberry.axiom.packet.impl;

import com.google.common.util.concurrent.RateLimiter;
import com.moulberry.axiom.*;
import com.moulberry.axiom.blueprint.ServerBlueprintManager;
import com.moulberry.axiom.event.AxiomHandshakeEvent;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.persistence.ItemStackDataType;
import com.moulberry.axiom.persistence.UUIDDataType;
import com.moulberry.axiom.viaversion.ViaVersionHelper;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.SharedConstants;
import net.minecraft.core.IdMapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class HelloPacketListener implements PacketHandler {

    private final AxiomPaper plugin;

    public HelloPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.hasAxiomPermission(player)) {
            return;
        }

        int apiVersion = friendlyByteBuf.readVarInt();

        if (apiVersion != AxiomConstants.API_VERSION) {
            String versions = " (C="+apiVersion+" S="+AxiomConstants.API_VERSION+")";
            Component text;
            if (apiVersion < AxiomConstants.API_VERSION) {
                text = Component.text("Unable to use Axiom, you're on an outdated version! Please update to the latest version of Axiom to use it on this server." + versions);
            } else {
                text = Component.text("Unable to use Axiom, server hasn't updated Axiom yet." + versions);
            }

            String unsupportedAxiomVersion = plugin.configuration.getString("unsupported-axiom-version");
            if (unsupportedAxiomVersion == null) unsupportedAxiomVersion = "kick";
            if (unsupportedAxiomVersion.equals("warn")) {
                player.sendMessage(text.color(NamedTextColor.RED));
                return;
            } else if (!unsupportedAxiomVersion.equals("ignore")) {
                player.kick(text);
                return;
            }
        }

        int dataVersion = friendlyByteBuf.readVarInt();
        int protocolVersion = friendlyByteBuf.readVarInt();

        int serverDataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        if (protocolVersion != SharedConstants.getProtocolVersion()) {
            String incompatibleDataVersion = plugin.configuration.getString("incompatible-data-version");
            if (incompatibleDataVersion == null) incompatibleDataVersion = "warn";

            Component incompatibleWarning = Component.text("Axiom: Incompatible data version detected (client " + dataVersion +
                ", server " + serverDataVersion  + ")");

            if (!Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                if (incompatibleDataVersion.equals("warn")) {
                    player.sendMessage(incompatibleWarning.color(NamedTextColor.RED));
                    return;
                } else if (!incompatibleDataVersion.equals("ignore")) {
                    player.kick(incompatibleWarning);
                    return;
                }
            } else {
                IdMapper<BlockState> mapper;
                try {
                    mapper = ViaVersionHelper.getBlockRegistryForVersion(this.plugin.allowedBlockRegistry, protocolVersion);
                } catch (Exception e) {
                    String clientDescription = "client: " + ProtocolVersion.getProtocol(protocolVersion);
                    String serverDescription = "server: " + ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion());
                    String description = clientDescription + " <-> " + serverDescription;
                    Component text = Component.text("Axiom+ViaVersion: " + e.getMessage() + " (" + description + ")");

                    if (incompatibleDataVersion.equals("warn")) {
                        player.sendMessage(text.color(NamedTextColor.RED));
                    } else {
                        player.kick(text);
                    }
                    return;
                }

                this.plugin.playerBlockRegistry.put(player.getUniqueId(), mapper);
                this.plugin.playerProtocolVersion.put(player.getUniqueId(), protocolVersion);

                Component text = Component.text("Axiom: Warning, client and server versions don't match. " +
                        "Axiom will try to use ViaVersion conversions, but this process may cause problems");
                player.sendMessage(text.color(NamedTextColor.RED));
            }
        }

        // Call handshake event
        int maxBufferSize = plugin.configuration.getInt("max-block-buffer-packet-size");
        AxiomHandshakeEvent handshakeEvent = new AxiomHandshakeEvent(player, maxBufferSize);
        Bukkit.getPluginManager().callEvent(handshakeEvent);
        if (handshakeEvent.isCancelled()) {
            return;
        }

        this.plugin.activeAxiomPlayers.add(player.getUniqueId());
        int rateLimit = this.plugin.configuration.getInt("block-buffer-rate-limit");
        if (rateLimit > 0) {
            this.plugin.playerBlockBufferRateLimiters.putIfAbsent(player.getUniqueId(), RateLimiter.create(rateLimit));
        }

        // Enable
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), MinecraftServer.getServer().registryAccess());
        buf.writeBoolean(true);
        buf.writeInt(handshakeEvent.getMaxBufferSize()); // Max Buffer Size
        buf.writeBoolean(false); // No source info
        buf.writeBoolean(false); // No source settings
        buf.writeVarInt(5); // Maximum Reach
        buf.writeVarInt(16); // Max editor views
        buf.writeBoolean(true); // Editable Views
        buf.writeVarInt(0); // No custom data overrides
        buf.writeVarInt(0); // No rotation overrides
        buf.writeVarInt(1); // Blueprint version

        byte[] enableBytes = new byte[buf.writerIndex()];
        buf.getBytes(0, enableBytes);
        player.sendPluginMessage(this.plugin, "axiom:enable", enableBytes);

        // Initialize Hotbars
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (!this.plugin.isMismatchedDataVersion(player.getUniqueId())) {
            int activeHotbarIndex = container.getOrDefault(AxiomConstants.ACTIVE_HOTBAR_INDEX, PersistentDataType.BYTE, (byte) 0);
            PersistentDataContainer hotbarItems = container.get(AxiomConstants.HOTBAR_DATA, PersistentDataType.TAG_CONTAINER);
            if (hotbarItems != null) {
                buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), MinecraftServer.getServer().registryAccess());
                buf.writeByte((byte) activeHotbarIndex);
                for (int i=0; i<9*9; i++) {
                    // Ignore selected hotbar
                    if (i / 9 == activeHotbarIndex) {
                        net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, net.minecraft.world.item.ItemStack.EMPTY);
                    } else {
                        ItemStack stack = hotbarItems.get(new NamespacedKey("axiom", "slot_"+i), ItemStackDataType.INSTANCE);
                        net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, CraftItemStack.asNMSCopy(stack));
                    }
                }

                byte[] bytes = new byte[buf.writerIndex()];
                buf.getBytes(0, bytes);
                player.sendPluginMessage(this.plugin, "axiom:initialize_hotbars", bytes);
            }
        }

        // Initialize Views
        UUID activeView = container.get(AxiomConstants.ACTIVE_VIEW, UUIDDataType.INSTANCE);
        if (activeView != null) {
            buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), MinecraftServer.getServer().registryAccess());
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
        ServerWorldPropertiesRegistry properties = plugin.getOrCreateWorldProperties(world);

        if (properties == null) {
            player.sendPluginMessage(plugin, "axiom:register_world_properties", new byte[]{0});
        } else {
            properties.registerFor(plugin, player);
        }

        WorldExtension.onPlayerJoin(world, player);

        ServerBlueprintManager.sendManifest(List.of(((CraftPlayer)player).getHandle()));
    }

}
