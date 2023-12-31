package com.moulberry.axiom;

import com.google.common.util.concurrent.RateLimiter;
import com.mojang.brigadier.StringReader;
import com.moulberry.axiom.buffer.BlockBuffer;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.event.AxiomCreateWorldPropertiesEvent;
import com.moulberry.axiom.event.AxiomModifyWorldEvent;
import com.moulberry.axiom.packet.*;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import io.papermc.paper.network.ChannelInitializeListener;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.key.Key;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.*;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class AxiomPaper extends JavaPlugin implements Listener {

    public static AxiomPaper PLUGIN; // tsk tsk tsk

    public final Set<UUID> activeAxiomPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public final Map<UUID, RateLimiter> playerBlockBufferRateLimiters = new ConcurrentHashMap<>();
    public Configuration configuration;

    public IdMapper<BlockState> allowedBlockRegistry = null;
    private boolean logLargeBlockBufferChanges = false;

    @Override
    public void onEnable() {
        PLUGIN = this;

        this.saveDefaultConfig();
        configuration = this.getConfig();

        Set<String> validResolutions = Set.of("kick", "warn", "ignore");
        if (!validResolutions.contains(configuration.getString("incompatible-data-version"))) {
            this.getLogger().warning("Invalid value for incompatible-data-version, expected 'kick', 'warn' or 'ignore'");
        }
        if (!validResolutions.contains(configuration.getString("unsupported-axiom-version"))) {
            this.getLogger().warning("Invalid value for unsupported-axiom-version, expected 'kick', 'warn' or 'ignore'");
        }

        this.logLargeBlockBufferChanges = this.configuration.getBoolean("log-large-block-buffer-changes");

        List<String> disallowedBlocks = this.configuration.getStringList("disallowed-blocks");
        this.allowedBlockRegistry = DisallowedBlocks.createAllowedBlockRegistry(disallowedBlocks);

        Bukkit.getPluginManager().registerEvents(this, this);
        // Bukkit.getPluginManager().registerEvents(new WorldPropertiesExample(), this);
        CompressedBlockEntity.initialize(this);

        Messenger msg = Bukkit.getMessenger();

        msg.registerOutgoingPluginChannel(this, "axiom:enable");
        msg.registerOutgoingPluginChannel(this, "axiom:initialize_hotbars");
        msg.registerOutgoingPluginChannel(this, "axiom:set_editor_views");
        msg.registerOutgoingPluginChannel(this, "axiom:response_chunk_data");
        msg.registerOutgoingPluginChannel(this, "axiom:register_world_properties");
        msg.registerOutgoingPluginChannel(this, "axiom:set_world_property");
        msg.registerOutgoingPluginChannel(this, "axiom:ack_world_properties");

        if (configuration.getBoolean("packet-handlers.hello")) {
            msg.registerIncomingPluginChannel(this, "axiom:hello", new HelloPacketListener(this));
        }
        if (configuration.getBoolean("packet-handlers.set-gamemode")) {
            msg.registerIncomingPluginChannel(this, "axiom:set_gamemode", new SetGamemodePacketListener(this));
        }
        if (configuration.getBoolean("packet-handlers.set-fly-speed")) {
            msg.registerIncomingPluginChannel(this, "axiom:set_fly_speed", new SetFlySpeedPacketListener(this));
        }
        if (configuration.getBoolean("packet-handlers.set-world-time")) {
            msg.registerIncomingPluginChannel(this, "axiom:set_world_time", new SetTimePacketListener(this));
        }
        if (configuration.getBoolean("packet-handlers.set-world-property")) {
            msg.registerIncomingPluginChannel(this, "axiom:set_world_property", new SetWorldPropertyListener(this));
        }
        if (configuration.getBoolean("packet-handlers.set-single-block")) {
            msg.registerIncomingPluginChannel(this, "axiom:set_block", new SetBlockPacketListener(this));
        }
        if (configuration.getBoolean("packet-handlers.set-hotbar-slot")) {
            msg.registerIncomingPluginChannel(this, "axiom:set_hotbar_slot", new SetHotbarSlotPacketListener(this));
        }
        if (configuration.getBoolean("packet-handlers.switch-active-hotbar")) {
            msg.registerIncomingPluginChannel(this, "axiom:switch_active_hotbar", new SwitchActiveHotbarPacketListener(this));
        }
        if (configuration.getBoolean("packet-handlers.teleport")) {
            msg.registerIncomingPluginChannel(this, "axiom:teleport", new TeleportPacketListener(this));
        }
        if (configuration.getBoolean("packet-handlers.set-editor-views")) {
            msg.registerIncomingPluginChannel(this, "axiom:set_editor_views", new SetEditorViewsPacketListener(this));
        }
        if (configuration.getBoolean("packet-handlers.request-chunk-data")) {
            msg.registerIncomingPluginChannel(this, "axiom:request_chunk_data", new RequestChunkDataPacketListener(this));
        }

        if (configuration.getBoolean("packet-handlers.set-buffer")) {
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
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            HashSet<UUID> stillActiveAxiomPlayers = new HashSet<>();

            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                if (activeAxiomPlayers.contains(player.getUniqueId())) {
                    if (!player.hasPermission("axiom.*")) {
                        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                        buf.writeBoolean(false);
                        byte[] bytes = new byte[buf.writerIndex()];
                        buf.getBytes(0, bytes);
                        player.sendPluginMessage(this, "axiom:enable", bytes);
                    } else {
                        stillActiveAxiomPlayers.add(player.getUniqueId());
                    }
                }
            }

            activeAxiomPlayers.retainAll(stillActiveAxiomPlayers);
            playerBlockBufferRateLimiters.keySet().retainAll(stillActiveAxiomPlayers);
        }, 20, 20);

        int maxChunkRelightsPerTick = configuration.getInt("max-chunk-relights-per-tick");
        int maxChunkSendsPerTick = configuration.getInt("max-chunk-sends-per-tick");

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            WorldExtension.tick(MinecraftServer.getServer(), maxChunkRelightsPerTick, maxChunkSendsPerTick);
        }, 1, 1);
    }

    public boolean logLargeBlockBufferChanges() {
        return this.logLargeBlockBufferChanges;
    }

    public boolean canUseAxiom(Player player) {
        return player.hasPermission("axiom.*") && activeAxiomPlayers.contains(player.getUniqueId());
    }

    public @Nullable RateLimiter getBlockBufferRateLimiter(UUID uuid) {
        return this.playerBlockBufferRateLimiters.get(uuid);
    }

    private final WeakHashMap<World, ServerWorldPropertiesRegistry> worldProperties = new WeakHashMap<>();

    public @Nullable ServerWorldPropertiesRegistry getWorldPropertiesIfPresent(World world) {
        return worldProperties.get(world);
    }

    public @Nullable ServerWorldPropertiesRegistry getOrCreateWorldProperties(World world) {
        if (worldProperties.containsKey(world)) {
            return worldProperties.get(world);
        } else {
            ServerWorldPropertiesRegistry properties = createWorldProperties(world);
            worldProperties.put(world, properties);
            return properties;
        }
    }

    public boolean canModifyWorld(Player player, World world) {
        String whitelist = this.configuration.getString("whitelist-world-regex");
        if (whitelist != null && !world.getName().matches(whitelist)) {
            return false;
        }

        String blacklist = this.configuration.getString("blacklist-world-regex");
        if (blacklist != null && world.getName().matches(blacklist)) {
            return false;
        }

        AxiomModifyWorldEvent modifyWorldEvent = new AxiomModifyWorldEvent(player, world);
        Bukkit.getPluginManager().callEvent(modifyWorldEvent);
        return !modifyWorldEvent.isCancelled();
    }

    @EventHandler
    public void onFailMove(PlayerFailMoveEvent event) {
        if (event.getPlayer().hasPermission("axiom.*")) {
            if (event.getFailReason() == PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY) {
                event.setAllowed(true); // Support for arcball camera
            } else if (event.getPlayer().isFlying()) {
                event.setAllowed(true); // Support for noclip
            }
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        World world = event.getPlayer().getWorld();

        ServerWorldPropertiesRegistry properties = getOrCreateWorldProperties(world);

        if (properties == null) {
            event.getPlayer().sendPluginMessage(this, "axiom:register_world_properties", new byte[]{0});
        } else {
            properties.registerFor(this, event.getPlayer());
        }
    }

    @EventHandler
    public void onGameRuleChanged(WorldGameRuleChangeEvent event) {
        if (event.getGameRule() == GameRule.DO_WEATHER_CYCLE) {
            ServerWorldPropertiesRegistry.PAUSE_WEATHER.setValue(event.getWorld(), !Boolean.parseBoolean(event.getValue()));
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
