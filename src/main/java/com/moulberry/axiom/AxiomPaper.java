package com.moulberry.axiom;

import com.moulberry.axiom.packet.*;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import io.papermc.paper.network.ChannelInitializeListener;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.key.Key;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class AxiomPaper extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "axiom:enable");
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "axiom:initialize_hotbars");
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "axiom:set_editor_views");

        HashSet<UUID> activeAxiomPlayers = new HashSet<>();

        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:hello", new HelloPacketListener(this, activeAxiomPlayers));
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_gamemode", new SetGamemodePacketListener());
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_fly_speed", new SetFlySpeedPacketListener());
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_block", new SetBlockPacketListener(this));
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_hotbar_slot", new SetHotbarSlotPacketListener());
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:switch_active_hotbar", new SwitchActiveHotbarPacketListener());
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:teleport", new TeleportPacketListener());
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "axiom:set_editor_views", new SetEditorViewsPacketListener());

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
