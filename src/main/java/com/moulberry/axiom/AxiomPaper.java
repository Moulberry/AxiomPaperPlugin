package com.moulberry.axiom;

import com.moulberry.axiom.packet.AxiomBigPayloadHandler;
import com.moulberry.axiom.packet.SetBlockBufferPacketListener;
import com.moulberry.axiom.packet.SetBlockPacketListener;
import com.moulberry.axiom.persistence.ItemStackDataType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.papermc.paper.network.ChannelInitializeListener;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import io.papermc.paper.network.ConnectionEvent;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.kyori.adventure.key.Key;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class AxiomPaper extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "axiom:enable");
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "axiom:initialize_hotbars");

        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_gamemode", (channel, player, message) -> {
            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
            GameType gameType = GameType.byId(friendlyByteBuf.readByte());
            ((CraftPlayer)player).getHandle().setGameMode(gameType);
        });
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_fly_speed", (channel, player, message) -> {
            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
            float flySpeed = friendlyByteBuf.readFloat();
            ((CraftPlayer)player).getHandle().getAbilities().setFlyingSpeed(flySpeed);
        });
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_block", new SetBlockPacketListener(this));
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_hotbar_slot", (channel, player, message) -> {
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
                    throw new RuntimeException("Failed ot find ServerboundCustomPayloadPacket id");
                }

                Connection connection = (Connection) channel.pipeline().get("packet_handler");
                channel.pipeline().addBefore("decoder", "axiom-big-payload-handler",
                                             new AxiomBigPayloadHandler(payloadId, connection, setBlockBufferPacketListener));
            }
        });

        // Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_block_buffer", new SetBlockBufferPacketListener(this));
    }

    private static final NamespacedKey ACTIVE_HOTBAR_INDEX = new NamespacedKey("axiom", "active_hotbar_index");
    private static final NamespacedKey HOTBAR_DATA = new NamespacedKey("axiom", "hotbar_data");

    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        Player player = event.getPlayer();
        String channel = event.getChannel();

        switch (channel) {
            case "axiom:enable" -> {
                FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                friendlyByteBuf.writeBoolean(true);
                friendlyByteBuf.writeByte(0); // todo: world properties
                player.sendPluginMessage(this, "axiom:enable", friendlyByteBuf.array());
            }
            case "axiom:initialize_hotbars" -> {
                PersistentDataContainer container = player.getPersistentDataContainer();
                int activeHotbarIndex = container.getOrDefault(ACTIVE_HOTBAR_INDEX, PersistentDataType.BYTE, (byte) 0);
                PersistentDataContainer hotbarItems = container.get(HOTBAR_DATA, PersistentDataType.TAG_CONTAINER);
                if (hotbarItems != null) {
                    FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                    friendlyByteBuf.writeByte((byte) activeHotbarIndex);
                    for (int i=0; i<9*9; i++) {
                        // Ignore selected hotbar
                        if (i / 9 == activeHotbarIndex) continue;

                        ItemStack stack = hotbarItems.get(new NamespacedKey("axiom", "slot_"+i), ItemStackDataType.INSTANCE);
                        friendlyByteBuf.writeItem(CraftItemStack.asNMSCopy(stack));
                    }
                    player.sendPluginMessage(this, "axiom:initialize_hotbars", friendlyByteBuf.array());
                }
            }
            default -> {}
        }
    }



}
