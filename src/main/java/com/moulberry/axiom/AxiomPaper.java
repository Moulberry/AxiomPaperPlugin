package com.moulberry.axiom;

import com.google.common.util.concurrent.RateLimiter;
import com.moulberry.axiom.blueprint.ServerBlueprintManager;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.commands.AxiomDebugCommand;
import com.moulberry.axiom.event.AxiomCreateWorldPropertiesEvent;
import com.moulberry.axiom.event.AxiomModifyWorldEvent;
import com.moulberry.axiom.integration.coreprotect.CoreProtectIntegration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.packet.*;
import com.moulberry.axiom.packet.impl.BlueprintRequestPacketListener;
import com.moulberry.axiom.packet.impl.DeleteEntityPacketListener;
import com.moulberry.axiom.packet.impl.HelloPacketListener;
import com.moulberry.axiom.packet.impl.ManipulateEntityPacketListener;
import com.moulberry.axiom.packet.impl.MarkerNbtRequestPacketListener;
import com.moulberry.axiom.packet.impl.RequestChunkDataPacketListener;
import com.moulberry.axiom.packet.impl.SetBlockBufferPacketListener;
import com.moulberry.axiom.packet.impl.SetBlockPacketListener;
import com.moulberry.axiom.packet.impl.SetEditorViewsPacketListener;
import com.moulberry.axiom.packet.impl.SetFlySpeedPacketListener;
import com.moulberry.axiom.packet.impl.SetGamemodePacketListener;
import com.moulberry.axiom.packet.impl.SetHotbarSlotPacketListener;
import com.moulberry.axiom.packet.impl.SetTimePacketListener;
import com.moulberry.axiom.packet.impl.SetWorldPropertyListener;
import com.moulberry.axiom.packet.impl.SpawnEntityPacketListener;
import com.moulberry.axiom.packet.impl.SwitchActiveHotbarPacketListener;
import com.moulberry.axiom.packet.impl.TeleportPacketListener;
import com.moulberry.axiom.packet.impl.UpdateAnnotationPacketListener;
import com.moulberry.axiom.packet.impl.UploadBlueprintPacketListener;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import io.papermc.paper.network.ChannelInitializeListener;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.key.Key;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMapper;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AxiomPaper extends JavaPlugin implements Listener {

    public static AxiomPaper PLUGIN; // tsk tsk tsk

    public final Set<UUID> activeAxiomPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public final Map<UUID, RateLimiter> playerBlockBufferRateLimiters = new ConcurrentHashMap<>();
    public final Map<UUID, Restrictions> playerRestrictions = new ConcurrentHashMap<>();
    public final Map<UUID, IdMapper<BlockState>> playerBlockRegistry = new ConcurrentHashMap<>();
    public final Map<UUID, Integer> playerProtocolVersion = new ConcurrentHashMap<>();
    public Configuration configuration;

    public IdMapper<BlockState> allowedBlockRegistry = null;
    private boolean logLargeBlockBufferChanges = false;

    public Path blueprintFolder = null;
    public boolean allowAnnotations = false;

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

        boolean allowLargeChunkDataRequest = this.configuration.getBoolean("allow-large-chunk-data-request");
        this.logLargeBlockBufferChanges = this.configuration.getBoolean("log-large-block-buffer-changes");

        List<String> disallowedBlocks = this.configuration.getStringList("disallowed-blocks");
        this.allowedBlockRegistry = DisallowedBlocks.createAllowedBlockRegistry(disallowedBlocks);

        this.allowAnnotations = this.configuration.getBoolean("allow-annotations");

        int allowedCapabilities = calculateAllowedCapabilities();
        int infiniteReachLimit = this.configuration.getInt("infinite-reach-limit");

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
        msg.registerOutgoingPluginChannel(this, "axiom:restrictions");
        msg.registerOutgoingPluginChannel(this, "axiom:marker_data");
        msg.registerOutgoingPluginChannel(this, "axiom:marker_nbt_response");
        msg.registerOutgoingPluginChannel(this, "axiom:annotation_update");

        Map<String, PacketHandler> largePayloadHandlers = new HashMap<>();

        registerPacketHandler("hello", new HelloPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("set_gamemode", new SetGamemodePacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("set_fly_speed", new SetFlySpeedPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("set_world_time", new SetTimePacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("set_world_property", new SetWorldPropertyListener(this), msg, largePayloadHandlers);
        registerPacketHandler("set_block", new SetBlockPacketListener(this), msg, largePayloadHandlers); // set-single-block
        registerPacketHandler("set_hotbar_slot", new SetHotbarSlotPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("switch_active_hotbar", new SwitchActiveHotbarPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("teleport", new TeleportPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("set_editor_views", new SetEditorViewsPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("request_chunk_data", new RequestChunkDataPacketListener(this,
            !configuration.getBoolean("packet-handlers.request-chunk-data")), msg, largePayloadHandlers);
        registerPacketHandler("spawn_entity", new SpawnEntityPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("manipulate_entity", new ManipulateEntityPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("delete_entity", new DeleteEntityPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("marker_nbt_request", new MarkerNbtRequestPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("request_blueprint", new BlueprintRequestPacketListener(this), msg, largePayloadHandlers);

        registerPacketHandler("set_buffer", new SetBlockBufferPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("upload_blueprint", new UploadBlueprintPacketListener(this), msg, largePayloadHandlers);
        registerPacketHandler("annotation_update", new UpdateAnnotationPacketListener(this), msg, largePayloadHandlers);

        if (!largePayloadHandlers.isEmpty()) {
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
                        new AxiomBigPayloadHandler(payloadId, connection, largePayloadHandlers));
                }
            });
        }

        if (this.configuration.getBoolean("blueprint-sharing")) {
            this.blueprintFolder = this.getDataFolder().toPath().resolve("blueprints");
            try {
                Files.createDirectories(this.blueprintFolder);
            } catch (IOException ignored) {}
            ServerBlueprintManager.initialize(this.blueprintFolder);
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            HashSet<UUID> stillActiveAxiomPlayers = new HashSet<>();

            int rateLimit = this.configuration.getInt("block-buffer-rate-limit");
            if (rateLimit > 0) {
                // Reduce by 20% just to prevent synchronization/timing issues
                rateLimit = rateLimit * 8/10;
                if (rateLimit <= 0) rateLimit = 1;
            }

            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                if (activeAxiomPlayers.contains(player.getUniqueId())) {
                    if (!this.hasAxiomPermission(player)) {
                        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                        buf.writeBoolean(false);
                        byte[] bytes = new byte[buf.writerIndex()];
                        buf.getBytes(0, bytes);
                        player.sendPluginMessage(this, "axiom:enable", bytes);
                    } else {
                        UUID uuid = player.getUniqueId();
                        stillActiveAxiomPlayers.add(uuid);

                        boolean send = false;

                        Restrictions restrictions = playerRestrictions.get(uuid);
                        if (restrictions == null) {
                            restrictions = new Restrictions();
                            playerRestrictions.put(uuid, restrictions);
                            send = true;
                        }

                        Set<PlotSquaredIntegration.PlotBox> bounds = Set.of();

                        if (!player.hasPermission("axiom.allow_copying_other_plots")) {
                            if (PlotSquaredIntegration.isPlotWorld(player.getWorld())) {
                                PlotSquaredIntegration.PlotBounds editable = PlotSquaredIntegration.getCurrentEditablePlot(player);
                                if (editable != null) {
                                    restrictions.lastPlotBounds = editable;
                                    bounds = editable.boxes();
                                } else if (restrictions.lastPlotBounds != null && restrictions.lastPlotBounds.worldName().equals(player.getWorld().getName())) {
                                    bounds = restrictions.lastPlotBounds.boxes();
                                } else {
                                    bounds = Set.of(new PlotSquaredIntegration.PlotBox(BlockPos.ZERO, BlockPos.ZERO));
                                }
                            }

                            if (bounds.size() == 1) {
                                PlotSquaredIntegration.PlotBox plotBounds = bounds.iterator().next();

                                int min = Integer.MIN_VALUE;
                                int max = Integer.MAX_VALUE;

                                if (plotBounds.min().getX() == min && plotBounds.min().getY() == min && plotBounds.min().getZ() == min &&
                                        plotBounds.max().getX() == max && plotBounds.max().getY() == max && plotBounds.max().getZ() == max) {
                                    bounds = Set.of();
                                }
                            }
                        }

                        boolean allowImportingBlocks = player.hasPermission("axiom.can_import_blocks");
                        boolean canCreateAnnotations = this.allowAnnotations && player.hasPermission("axiom.annotation.create");

                        if (restrictions.maxSectionsPerSecond != rateLimit ||
                                restrictions.canImportBlocks != allowImportingBlocks ||
                                restrictions.canCreateAnnotations != canCreateAnnotations ||
                                restrictions.allowedCapabilities != allowedCapabilities ||
                                restrictions.infiniteReachLimit != infiniteReachLimit ||
                                !Objects.equals(restrictions.bounds, bounds)) {
                            restrictions.maxSectionsPerSecond = rateLimit;
                            restrictions.canImportBlocks = allowImportingBlocks;
                            restrictions.canCreateAnnotations = canCreateAnnotations;
                            restrictions.allowedCapabilities = allowedCapabilities;
                            restrictions.infiniteReachLimit = infiniteReachLimit;
                            restrictions.bounds = bounds;
                            send = true;
                        }

                        if (send) {
                            restrictions.send(this, player);
                        }
                    }
                }
            }

            activeAxiomPlayers.retainAll(stillActiveAxiomPlayers);
            playerBlockBufferRateLimiters.keySet().retainAll(stillActiveAxiomPlayers);
            playerRestrictions.keySet().retainAll(stillActiveAxiomPlayers);
            playerBlockRegistry.keySet().retainAll(stillActiveAxiomPlayers);
            playerProtocolVersion.keySet().retainAll(stillActiveAxiomPlayers);
        }, 20, 20);

        boolean sendMarkers = configuration.getBoolean("send-markers");
        int maxChunkRelightsPerTick = configuration.getInt("max-chunk-relights-per-tick");
        int maxChunkSendsPerTick = configuration.getInt("max-chunk-sends-per-tick");

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            WorldExtension.tick(MinecraftServer.getServer(), sendMarkers, maxChunkRelightsPerTick, maxChunkSendsPerTick);
        }, 1, 1);

        try {
            LegacyPaperCommandManager<CommandSender> manager = LegacyPaperCommandManager.createNative(
                this,
                ExecutionCoordinator.simpleCoordinator()
            );
            if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
                manager.registerBrigadier();
            }
            AxiomDebugCommand.register(this, manager);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (CoreProtectIntegration.isEnabled()) {
            this.getLogger().info("CoreProtect integration enabled");
        }
    }

    private void registerPacketHandler(String name, PacketHandler handler, Messenger messenger, Map<String, PacketHandler> largePayloadHandlers) {
        String configEntry = "packet-handlers." + name.replace("_", "-");
        if (name.equals("set_block")) {
            configEntry = "packet-handlers.set-single-block";
        } else if (name.equals("request_blueprint")) {
            configEntry = "packet-handlers.blueprint-request";
        }
        if (name.equals("request-chunk-data") || this.configuration.getBoolean(configEntry, true)) {
            boolean isLargePayload = false;

            if (name.equals("hello")) { // Hello must use normal system, as non-Axiom players can't send large payloads
                isLargePayload = false;
            } else if (this.configuration.getBoolean("allow-large-payload-for-all-packets")) {
                isLargePayload = true;
            } else if (name.equals("set_buffer") || name.equals("upload_blueprint") || name.equals("annotation_update")) {
                isLargePayload = true;
            } else if (name.equals("request_chunk_data") && this.configuration.getBoolean("allow-large-chunk-data-request")) {
                isLargePayload = true;
            }

            if (isLargePayload) {
                largePayloadHandlers.put("axiom:"+name, handler);
                messenger.registerIncomingPluginChannel(this, "axiom:"+name, new DummyPacketListener());
            } else {
                messenger.registerIncomingPluginChannel(this, "axiom:"+name, new WrapperPacketListener(handler));
            }
        }
    }

    private int calculateAllowedCapabilities() {
        Set<String> allowed = new HashSet<>(this.configuration.getStringList("allow-capabilities"));
        if (allowed.contains("all")) {
            return -1;
        }

        int allowedCapabilities = 0;
        if (allowed.contains("bulldozer")) allowedCapabilities |= Restrictions.ALLOW_BULLDOZER;
        if (allowed.contains("replace_mode")) allowedCapabilities |= Restrictions.ALLOW_REPLACE_MODE;
        if (allowed.contains("force_place")) allowedCapabilities |= Restrictions.ALLOW_FORCE_PLACE;
        if (allowed.contains("no_updates")) allowedCapabilities |= Restrictions.ALLOW_NO_UPDATES;
        if (allowed.contains("tinker")) allowedCapabilities |= Restrictions.ALLOW_TINKER;
        if (allowed.contains("infinite_reach")) allowedCapabilities |= Restrictions.ALLOW_INFINITE_REACH;
        if (allowed.contains("fast_place")) allowedCapabilities |= Restrictions.ALLOW_FAST_PLACE;
        if (allowed.contains("angel_placement")) allowedCapabilities |= Restrictions.ALLOW_ANGEL_PLACEMENT;
        if (allowed.contains("no_clip")) allowedCapabilities |= Restrictions.ALLOW_NO_CLIP;
        return allowedCapabilities;
    }

    public boolean logLargeBlockBufferChanges() {
        return this.logLargeBlockBufferChanges;
    }

    public boolean hasAxiomPermission(Player player) {
        return hasAxiomPermission(player, null, false);
    }

    public boolean hasAxiomPermission(Player player, String permission, boolean strict) {
        if (player.hasPermission("axiom.*") || player.isOp()) {
            return !strict || permission == null || player.hasPermission("axiom.all") || player.hasPermission(permission);
        } else if (permission != null && !player.hasPermission(permission)) {
            return false;
        }
        return player.hasPermission("axiom.use");
    }

    public boolean canUseAxiom(Player player) {
        return canUseAxiom(player, null, false);
    }

    public boolean canUseAxiom(Player player, String permission) {
        return canUseAxiom(player, permission, false);
    }

    public boolean canUseAxiom(Player player, String permission, boolean strict) {
        return activeAxiomPlayers.contains(player.getUniqueId()) && hasAxiomPermission(player, permission, strict);
    }

    public @Nullable RateLimiter getBlockBufferRateLimiter(UUID uuid) {
        return this.playerBlockBufferRateLimiters.get(uuid);
    }

    public boolean isMismatchedDataVersion(UUID uuid) {
        return this.playerProtocolVersion.containsKey(uuid);
    }

    public int getProtocolVersionFor(UUID uuid) {
        return this.playerProtocolVersion.getOrDefault(uuid, SharedConstants.getProtocolVersion());
    }

    public IdMapper<BlockState> getBlockRegistry(UUID uuid) {
        return this.playerBlockRegistry.getOrDefault(uuid, this.allowedBlockRegistry);
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
        if (!this.activeAxiomPlayers.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getFailReason() == PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY) {
            event.setAllowed(true); // Support for arcball camera
        } else if (event.getPlayer().isFlying()) {
            event.setAllowed(true); // Support for noclip
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (!this.activeAxiomPlayers.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        World world = event.getPlayer().getWorld();

        ServerWorldPropertiesRegistry properties = getOrCreateWorldProperties(world);

        if (properties == null) {
            event.getPlayer().sendPluginMessage(this, "axiom:register_world_properties", new byte[]{0});
        } else {
            properties.registerFor(this, event.getPlayer());
        }

        WorldExtension.onPlayerJoin(world, event.getPlayer());
    }

    @EventHandler
    public void onGameRuleChanged(WorldGameRuleChangeEvent event) {
        if (event.getGameRule() == GameRule.DO_WEATHER_CYCLE) {
            ServerWorldPropertiesRegistry.PAUSE_WEATHER.setValue(event.getWorld(), !Boolean.parseBoolean(event.getValue()));
        }
    }

    private ServerWorldPropertiesRegistry createWorldProperties(World world) {
        ServerWorldPropertiesRegistry registry = new ServerWorldPropertiesRegistry(new WeakReference<>(world));

        AxiomCreateWorldPropertiesEvent createEvent = new AxiomCreateWorldPropertiesEvent(world, registry);
        Bukkit.getPluginManager().callEvent(createEvent);
        if (createEvent.isCancelled()) return null;

        return registry;
    }

}
