package com.moulberry.axiom;

import com.moulberry.axiom.packet.AxiomBigPayloadHandler;
import com.moulberry.axiom.packet.SetBlockBufferPacketListener;
import com.moulberry.axiom.packet.SetBlockPacketListener;
import com.moulberry.axiom.persistence.ItemStackDataType;
import com.moulberry.axiom.persistence.UUIDDataType;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import io.papermc.paper.network.ChannelInitializeListener;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AxiomPaper extends JavaPlugin implements Listener {

    public static final long MIN_POSITION_LONG = BlockPos.asLong(-33554432, -2048, -33554432);
    static {
        if (MIN_POSITION_LONG != 0b1000000000000000000000000010000000000000000000000000100000000000L) {
            throw new Error("BlockPos representation changed!");
        }
    }

    private static final int API_VERSION = 4;
    private static final NamespacedKey ACTIVE_HOTBAR_INDEX = new NamespacedKey("axiom", "active_hotbar_index");
    private static final NamespacedKey HOTBAR_DATA = new NamespacedKey("axiom", "hotbar_data");

    private static final NamespacedKey ACTIVE_VIEW = new NamespacedKey("axiom", "active_view");
    private static final NamespacedKey VIEWS = new NamespacedKey("axiom", "views");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "axiom:enable");
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "axiom:initialize_hotbars");
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "axiom:set_editor_views");

        HashSet<UUID> activeAxiomPlayers = new HashSet<>();

        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:hello", (channel, player, message) -> {
            if (!player.hasPermission("axiom.*")) {
                return;
            }

            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
            int apiVersion = friendlyByteBuf.readVarInt();
            friendlyByteBuf.readNbt(); // Discard

            if (apiVersion != API_VERSION) {
                player.kick(Component.text("Unsupported Axiom API Version. Server supports " + API_VERSION +
                    ", while client is " + apiVersion));
                return;
            }

            activeAxiomPlayers.add(player.getUniqueId());

            // Enable
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(true);
            buf.writeByte(0); // todo: world properties
            buf.writeInt(0x100000); // Max Buffer Size
            buf.writeBoolean(false); // No source info
            buf.writeBoolean(false); // No source settings
            buf.writeVarInt(5); // Maximum Reach
            buf.writeVarInt(16); // Max editor views
            buf.writeBoolean(true); // Editable Views
            player.sendPluginMessage(this, "axiom:enable", buf.accessByteBufWithCorrectSize());

            // Initialize Hotbars
            PersistentDataContainer container = player.getPersistentDataContainer();
            int activeHotbarIndex = container.getOrDefault(ACTIVE_HOTBAR_INDEX, PersistentDataType.BYTE, (byte) 0);
            PersistentDataContainer hotbarItems = container.get(HOTBAR_DATA, PersistentDataType.TAG_CONTAINER);
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
                player.sendPluginMessage(this, "axiom:initialize_hotbars", buf.accessByteBufWithCorrectSize());
            }

            // Initialize Views
            UUID activeView = container.get(ACTIVE_VIEW, UUIDDataType.INSTANCE);
            if (activeView != null) {
                buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUUID(activeView);

                PersistentDataContainer[] views = container.get(VIEWS, PersistentDataType.TAG_CONTAINER_ARRAY);
                buf.writeVarInt(views.length);
                for (PersistentDataContainer view : views) {
                    View.load(view).write(buf);
                }

                player.sendPluginMessage(this, "axiom:set_editor_views", buf.accessByteBufWithCorrectSize());
            }
        });
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_gamemode", (channel, player, message) -> {
            if (!player.hasPermission("axiom.*")) {
                return;
            }

            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
            GameType gameType = GameType.byId(friendlyByteBuf.readByte());
            ((CraftPlayer)player).getHandle().setGameMode(gameType);
        });
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_fly_speed", (channel, player, message) -> {
            if (!player.hasPermission("axiom.*")) {
                return;
            }

            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
            float flySpeed = friendlyByteBuf.readFloat();
            ((CraftPlayer)player).getHandle().getAbilities().setFlyingSpeed(flySpeed);
        });
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_block", new SetBlockPacketListener(this));
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_hotbar_slot", (channel, player, message) -> {
            if (!player.hasPermission("axiom.*")) {
                return;
            }

            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
            int index = friendlyByteBuf.readByte();
            if (index < 0 || index >= 9*9) return;
            net.minecraft.world.item.ItemStack nmsStack = friendlyByteBuf.readItem();

            PersistentDataContainer container = player.getPersistentDataContainer();
            PersistentDataContainer hotbarItems = container.get(HOTBAR_DATA, PersistentDataType.TAG_CONTAINER);
            if (hotbarItems == null) hotbarItems = container.getAdapterContext().newPersistentDataContainer();
            hotbarItems.set(new NamespacedKey("axiom", "slot_"+index), ItemStackDataType.INSTANCE, CraftItemStack.asCraftMirror(nmsStack));
            container.set(HOTBAR_DATA, PersistentDataType.TAG_CONTAINER, hotbarItems);
        });
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:switch_active_hotbar", (channel, player, message) -> {
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
            PersistentDataContainer containerHotbarItems = container.get(HOTBAR_DATA, PersistentDataType.TAG_CONTAINER);
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

            container.set(HOTBAR_DATA, PersistentDataType.TAG_CONTAINER, containerHotbarItems);
            container.set(ACTIVE_HOTBAR_INDEX, PersistentDataType.BYTE, (byte) activeHotbarIndex);
        });
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:teleport", (channel, player, message) -> {
            if (!player.hasPermission("axiom.*")) {
                return;
            }

            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
            ResourceKey<Level> resourceKey = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
            double x = friendlyByteBuf.readDouble();
            double y = friendlyByteBuf.readDouble();
            double z = friendlyByteBuf.readDouble();
            float yRot = friendlyByteBuf.readFloat();
            float xRot = friendlyByteBuf.readFloat();

            NamespacedKey namespacedKey = new NamespacedKey(resourceKey.location().getNamespace(), resourceKey.location().getPath());
            World world = Bukkit.getWorld(namespacedKey);
            if (world != null) {
                player.teleport(new Location(world, x, y, z, yRot, xRot));
            }
        });
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_editor_views", (channel, player, message) -> {
            if (!player.hasPermission("axiom.*")) {
                return;
            }

            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
            UUID uuid = friendlyByteBuf.readUUID();
            List<View> views = friendlyByteBuf.readList(View::read);

            PersistentDataContainer container = player.getPersistentDataContainer();
            container.set(ACTIVE_VIEW, UUIDDataType.INSTANCE, uuid);

            PersistentDataContainer[] containerArray = new PersistentDataContainer[views.size()];
            for (int i = 0; i < views.size(); i++) {
                PersistentDataContainer viewContainer = container.getAdapterContext().newPersistentDataContainer();
                views.get(i).save(viewContainer);
                containerArray[i] = viewContainer;
            }
            container.set(VIEWS, PersistentDataType.TAG_CONTAINER_ARRAY, containerArray);
        });

        SetBlockBufferPacketListener setBlockBufferPacketListener = new SetBlockBufferPacketListener(this);

        ChannelInitializeListenerHolder.addListener(Key.key("axiom:handle_big_payload"), new ChannelInitializeListener() {
            @Override
            public void afterInitChannel(@NonNull Channel channel) {
                var packets = ConnectionProtocol.PLAY.getPacketsByIds(PacketFlow.SERVERBOUND);
                int payloadId = -1;
                for (Map.Entry<Integer, Class<? extends Packet<?>>> entry : packets.entrySet()) {
                    if (entry.getValue() == ServerboundCustomPayloadPacket.class) {
                        payloadId = entry.getKey();
                        break;
                    }
                }
                if (payloadId < 0) {
                    throw new RuntimeException("Failed to find ServerboundCustomPayloadPacket id");
                }

                Connection connection = (Connection) channel.pipeline().get("packet_handler");
                channel.pipeline().addBefore("decoder", "axiom-big-payload-handler",
                                             new AxiomBigPayloadHandler(payloadId, connection, setBlockBufferPacketListener));
            }
        });

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            HashSet<UUID> newActiveAxiomPlayers = new HashSet<>();

            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                if (activeAxiomPlayers.contains(player.getUniqueId())) {
                    if (!player.hasPermission("axiom.*")) {
                        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                        buf.writeBoolean(false);
                        player.sendPluginMessage(this, "axiom:enable", buf.accessByteBufWithCorrectSize());
                    } else {
                        newActiveAxiomPlayers.add(player.getUniqueId());
                    }
                }
            }

            activeAxiomPlayers.clear();
            activeAxiomPlayers.addAll(newActiveAxiomPlayers);
        }, 20, 20);
    }

    @EventHandler
    public void onFailMove(PlayerFailMoveEvent event) {
        if (event.getPlayer().hasPermission("axiom.*") &&
                event.getFailReason() == PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY) {
            event.setAllowed(true);
        }
    }

}
