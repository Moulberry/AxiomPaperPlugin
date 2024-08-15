package com.moulberry.axiom.packet;

import com.google.common.util.concurrent.RateLimiter;
import com.moulberry.axiom.*;
import com.moulberry.axiom.blueprint.ServerBlueprintManager;
import com.moulberry.axiom.event.AxiomHandshakeEvent;
import com.moulberry.axiom.persistence.ItemStackDataType;
import com.moulberry.axiom.persistence.UUIDDataType;
import com.moulberry.axiom.viaversion.ViaVersionHelper;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import com.viaversion.viaversion.api.Via;
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
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class HelloPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;

    public HelloPacketListener(AxiomPaper plugin) {
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

    private static int normalizeDataVersion(int dataVersion) {
        if (dataVersion == 3955) { // 1.21.1
            return 3953; // 1.21
        }
        return dataVersion;
    }

    private void process(Player player, byte[] message) {
        if (!this.plugin.hasAxiomPermission(player)) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        int apiVersion = friendlyByteBuf.readVarInt();
        int dataVersion = friendlyByteBuf.readVarInt();
        // note - skipping NBT here. friendlyByteBuf.readNBT();

        int serverDataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        if (normalizeDataVersion(dataVersion) != normalizeDataVersion(serverDataVersion)) {
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
                int playerVersion = Via.getAPI().getPlayerVersion(player.getUniqueId());
                if (playerVersion == SharedConstants.getProtocolVersion()) {
                    // Likely using via on the proxy, try to get protocol version from data version
                    if (dataVersion < 3337) {
                        player.sendMessage(incompatibleWarning.color(NamedTextColor.RED));
                        return;
                    } else if (dataVersion == 3337) {
                        playerVersion = 762; // 1.19.4
                    } else if (dataVersion <= 3465) {
                        playerVersion = 763; // 1.20.1
                    } else if (dataVersion <= 3578) {
                        playerVersion = 764; // 1.20.2
                    } else if (dataVersion <= 3700) {
                        playerVersion = 765; // 1.20.3 / 1.20.4
                    } else if (dataVersion <= 3837) {
                        playerVersion = 766; // 1.20.3 / 1.20.4
                    } else {
                        player.sendMessage(incompatibleWarning.color(NamedTextColor.RED));
                        return;
                    }
                }

                IdMapper<BlockState> mapper;
                try {
                    mapper = ViaVersionHelper.getBlockRegistryForVersion(this.plugin.allowedBlockRegistry, playerVersion);
                } catch (Exception e) {
                    String clientDescription = "client: " + ProtocolVersion.getProtocol(playerVersion);
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
                this.plugin.playerProtocolVersion.put(player.getUniqueId(), playerVersion);

                Component text = Component.text("Axiom: Warning, client and server versions don't match. " +
                        "Axiom will try to use ViaVersion conversions, but this process may cause problems");
                player.sendMessage(text.color(NamedTextColor.RED));
            }
        }

        if (apiVersion != AxiomConstants.API_VERSION) {
            Component text = Component.text("Unsupported Axiom API Version. Server supports " + AxiomConstants.API_VERSION +
                ", while client is " + apiVersion);

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

        if (!player.getListeningPluginChannels().contains("axiom:restrictions")) {
            Component text = Component.text("This server requires the use of Axiom 2.3 or later. Contact the server administrator if you believe this is unintentional");

            String unsupportedRestrictions = plugin.configuration.getString("client-doesnt-support-restrictions");
            if (unsupportedRestrictions == null) unsupportedRestrictions = "warn";
            if (unsupportedRestrictions.equals("warn")) {
                player.sendMessage(text.color(NamedTextColor.RED));
                return;
            } else if (!unsupportedRestrictions.equals("ignore")) {
                player.kick(text);
                return;
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
