package com.moulberry.axiom;

import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.event.AxiomCreateWorldPropertiesEvent;
import com.moulberry.axiom.event.AxiomTimeChangeEvent;
import com.moulberry.axiom.packet.*;
import com.moulberry.axiom.world_properties.WorldPropertyCategory;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import com.moulberry.axiom.world_properties.server.ServerWorldProperty;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import io.papermc.paper.network.ChannelInitializeListener;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.key.Key;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AxiomPaper extends JavaPlugin implements Listener {

    public static AxiomPaper PLUGIN; // tsk tsk tsk

    public final Set<UUID> activeAxiomPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onEnable() {
        PLUGIN = this;

        Bukkit.getPluginManager().registerEvents(this, this);
        // Bukkit.getPluginManager().registerEvents(new WorldPropertiesExample(), this);
        CompressedBlockEntity.initialize(this);

        Messenger msg = Bukkit.getMessenger();

        msg.registerOutgoingPluginChannel(this, "axiom:enable");
        msg.registerOutgoingPluginChannel(this, "axiom:initialize_hotbars");
        msg.registerOutgoingPluginChannel(this, "axiom:set_editor_views");
        msg.registerOutgoingPluginChannel(this, "axiom:block_entities");
        msg.registerOutgoingPluginChannel(this, "axiom:register_world_properties");
        msg.registerOutgoingPluginChannel(this, "axiom:set_world_property");
        msg.registerOutgoingPluginChannel(this, "axiom:ack_world_properties");

        msg.registerIncomingPluginChannel(this, "axiom:hello", new HelloPacketListener(this, activeAxiomPlayers));
        msg.registerIncomingPluginChannel(this, "axiom:set_gamemode", new SetGamemodePacketListener());
        msg.registerIncomingPluginChannel(this, "axiom:set_fly_speed", new SetFlySpeedPacketListener());
        msg.registerIncomingPluginChannel(this, "axiom:set_world_time", new SetTimePacketListener());
        msg.registerIncomingPluginChannel(this, "axiom:set_world_property", new SetWorldPropertyListener());
        msg.registerIncomingPluginChannel(this, "axiom:set_block", new SetBlockPacketListener(this));
        msg.registerIncomingPluginChannel(this, "axiom:set_hotbar_slot", new SetHotbarSlotPacketListener());
        msg.registerIncomingPluginChannel(this, "axiom:switch_active_hotbar", new SwitchActiveHotbarPacketListener());
        msg.registerIncomingPluginChannel(this, "axiom:teleport", new TeleportPacketListener());
        msg.registerIncomingPluginChannel(this, "axiom:set_editor_views", new SetEditorViewsPacketListener());
        msg.registerIncomingPluginChannel(this, "axiom:request_block_entity", new RequestBlockEntityPacketListener(this));

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

    private final WeakHashMap<World, ServerWorldPropertiesRegistry> worldProperties = new WeakHashMap<>();

    public @Nullable ServerWorldPropertiesRegistry getWorldProperties(World world) {
        if (worldProperties.containsKey(world)) {
            return worldProperties.get(world);
        } else {
            ServerWorldPropertiesRegistry properties = createWorldProperties(world);
            worldProperties.put(world, properties);
            return properties;
        }
    }

    @EventHandler
    public void onFailMove(PlayerFailMoveEvent event) {
        if (event.getPlayer().hasPermission("axiom.*") &&
            event.getFailReason() == PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY) {
            event.setAllowed(true);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        World world = event.getPlayer().getWorld();

        ServerWorldPropertiesRegistry properties = getWorldProperties(world);

        if (properties == null) {
            event.getPlayer().sendPluginMessage(this, "axiom:register_world_properties", new byte[]{0});
        } else {
            properties.registerFor(this, event.getPlayer());
        }
    }

    @EventHandler
    public void onGameRuleChanged(WorldGameRuleChangeEvent event) {
        if (event.getGameRule() == GameRule.DO_WEATHER_CYCLE) {
            ServerWorldPropertiesRegistry properties = getWorldProperties(event.getWorld());
            if (properties != null) {
                ServerWorldProperty<?> property = properties.getById(new ResourceLocation("axiom:pause_weather"));
                if (property != null) {
                    ((ServerWorldProperty<Boolean>)property).setValue(event.getWorld(), !Boolean.parseBoolean(event.getValue()));
                }
            }
        }
    }

    private ServerWorldPropertiesRegistry createWorldProperties(World world) {
        ServerWorldPropertiesRegistry registry = new ServerWorldPropertiesRegistry(world);

        AxiomCreateWorldPropertiesEvent createEvent = new AxiomCreateWorldPropertiesEvent(world, registry);
        Bukkit.getPluginManager().callEvent(createEvent);
        if (createEvent.isCancelled()) return null;

        return registry;
    }

}
