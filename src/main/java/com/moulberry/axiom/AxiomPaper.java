package com.moulberry.axiom;

import com.moulberry.axiom.blueprint.ServerBlueprintManager;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.commands.AxiomDebugCommand;
import com.moulberry.axiom.event.AxiomCreateWorldPropertiesEvent;
import com.moulberry.axiom.event.AxiomModifyWorldEvent;
import com.moulberry.axiom.integration.coreprotect.CoreProtectIntegration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.listener.NoPhysicalTriggerListener;
import com.moulberry.axiom.operations.OperationQueue;
import com.moulberry.axiom.operations.PendingOperation;
import com.moulberry.axiom.packet.*;
import com.moulberry.axiom.packet.impl.*;
import com.moulberry.axiom.paperapi.display.ImplServerCustomDisplays;
import com.moulberry.axiom.paperapi.entity.ImplAxiomHiddenEntities;
import com.moulberry.axiom.paperapi.block.ImplServerCustomBlocks;
import com.moulberry.axiom.restrictions.AxiomPermission;
import com.moulberry.axiom.restrictions.AxiomPermissionSet;
import com.moulberry.axiom.restrictions.Restrictions;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import io.papermc.paper.network.ChannelInitializeListener;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.util.TriState;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMapper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.*;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.server.PluginDisableEvent;
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
import java.util.function.IntFunction;

public class AxiomPaper extends JavaPlugin implements Listener {

    public static AxiomPaper PLUGIN; // tsk tsk tsk

    public final Set<UUID> activeAxiomPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public final Set<UUID> failedPermissionAxiomPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public final Map<UUID, Restrictions> playerRestrictions = new ConcurrentHashMap<>();
    public final Map<UUID, IdMapper<BlockState>> playerBlockRegistry = new ConcurrentHashMap<>();
    public final Map<UUID, Integer> playerProtocolVersion = new ConcurrentHashMap<>();
    private final Map<UUID, AxiomPermissionSet> playerPermissions = new HashMap<>();
    private final Map<UUID, PlotSquaredIntegration.PlotBounds> lastPlotBoundsForPlayers = new HashMap<>();
    private final Set<UUID> noPhysicalTriggerPlayers = new HashSet<>();
    private final OperationQueue operationQueue = new OperationQueue();
    private final Object2IntOpenHashMap<UUID> availableDispatchSends = new Object2IntOpenHashMap<>();
    public Configuration configuration;

    public IdMapper<BlockState> allowedBlockRegistry = null;
    private boolean logLargeBlockBufferChanges = false;
    private int packetCollectionReadLimit = 1024;
    private long maxNbtDecompressLimit = 131072;
    private final Set<EntityType<?>> whitelistedEntities = new HashSet<>();
    private final Set<EntityType<?>> blacklistedEntities = new HashSet<>();

    private int allowedDispatchSendsPerSecond = 1024;

    private boolean registeredNoPhysicalTriggerListener = false;
    public boolean logCoreProtectChanges = true;

    public Path blueprintFolder = null;
    public boolean allowAnnotations = false;
    private int infiniteReachLimit = -1;
    private boolean sendMarkers = false;
    private int maxChunkRelightsPerTick = 0;
    private int maxChunkSendsPerTick = 0;
    private int maxChunkLoadDistance = 256;

    @Override
    public void onEnable() {
        PLUGIN = this;

        AxiomReflection.init();

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

        if (this.configuration.getBoolean("allow-large-payload-for-all-packets")) {
            packetCollectionReadLimit = Short.MAX_VALUE;
            maxNbtDecompressLimit = Long.MAX_VALUE;
        }

        this.whitelistedEntities.clear();
        this.blacklistedEntities.clear();
        for (String whitelistedEntity : this.configuration.getStringList("whitelist-entities")) {
            EntityType.byString(whitelistedEntity).ifPresent(this.whitelistedEntities::add);
        }
        for (String blacklistedEntity : this.configuration.getStringList("blacklist-entities")) {
            EntityType.byString(blacklistedEntity).ifPresent(this.blacklistedEntities::add);
        }

        List<String> disallowedBlocks = this.configuration.getStringList("disallowed-blocks");
        this.allowedBlockRegistry = DisallowedBlocks.createAllowedBlockRegistry(disallowedBlocks);

        this.allowAnnotations = this.configuration.getBoolean("allow-annotations");
        boolean allowServerBlueprints = this.configuration.getBoolean("blueprint-sharing");

        this.infiniteReachLimit = this.configuration.getInt("infinite-reach-limit");

        Bukkit.getPluginManager().registerEvents(this, this);
        // Bukkit.getPluginManager().registerEvents(new WorldPropertiesExample(), this);
        CompressedBlockEntity.initialize(this);

        Messenger msg = Bukkit.getMessenger();

        msg.registerOutgoingPluginChannel(this, "axiom:enable");
        msg.registerOutgoingPluginChannel(this, "axiom:response_chunk_data");
        msg.registerOutgoingPluginChannel(this, "axiom:register_world_properties");
        msg.registerOutgoingPluginChannel(this, "axiom:set_world_property");
        msg.registerOutgoingPluginChannel(this, "axiom:ack_world_properties");
        msg.registerOutgoingPluginChannel(this, "axiom:restrictions");
        msg.registerOutgoingPluginChannel(this, "axiom:marker_data");
        msg.registerOutgoingPluginChannel(this, "axiom:marker_nbt_response");
        msg.registerOutgoingPluginChannel(this, "axiom:annotation_update");
        msg.registerOutgoingPluginChannel(this, "axiom:add_server_heightmap");
        msg.registerOutgoingPluginChannel(this, "axiom:custom_blocks");
        msg.registerOutgoingPluginChannel(this, "axiom:register_custom_block_v2");
        msg.registerOutgoingPluginChannel(this, "axiom:ignore_display_entities");
        msg.registerOutgoingPluginChannel(this, "axiom:register_custom_items");

        Map<String, PacketHandler> largePayloadHandlers = new HashMap<>();

        registerPacketHandler("hello", new HelloPacketListener(this), msg, LargePayloadBehaviour.FORCE_SMALL, largePayloadHandlers);
        registerPacketHandler("set_gamemode", new SetGamemodePacketListener(this), msg, LargePayloadBehaviour.FORCE_SMALL, largePayloadHandlers);
        registerPacketHandler("set_fly_speed", new SetFlySpeedPacketListener(this), msg, LargePayloadBehaviour.FORCE_SMALL, largePayloadHandlers);
        registerPacketHandler("teleport", new TeleportPacketListener(this), msg, LargePayloadBehaviour.FORCE_SMALL, largePayloadHandlers);
        registerPacketHandler("set_world_time", new SetTimePacketListener(this), msg, LargePayloadBehaviour.FORCE_SMALL, largePayloadHandlers);
        registerPacketHandler("set_no_physical_trigger", new SetNoPhysicalTriggerPacketListener(this), msg, LargePayloadBehaviour.FORCE_SMALL, largePayloadHandlers);
        registerPacketHandler("set_world_property", new SetWorldPropertyListener(this), msg, LargePayloadBehaviour.FORCE_SMALL, largePayloadHandlers);

        registerPacketHandler("request_chunk_data", new RequestChunkDataPacketListener(this), msg,
                this.configuration.getBoolean("allow-large-chunk-data-request") ? LargePayloadBehaviour.FORCE_LARGE : LargePayloadBehaviour.DEFAULT, largePayloadHandlers);
        registerPacketHandler("request_entity_data", new RequestEntityDataPacketListener(this), msg,
                this.configuration.getBoolean("allow-large-chunk-data-request") ? LargePayloadBehaviour.FORCE_LARGE : LargePayloadBehaviour.DEFAULT, largePayloadHandlers);

        registerPacketHandler("spawn_entity", new SpawnEntityPacketListener(this), msg, LargePayloadBehaviour.DEFAULT, largePayloadHandlers);
        registerPacketHandler("manipulate_entity", new ManipulateEntityPacketListener(this), msg, LargePayloadBehaviour.DEFAULT, largePayloadHandlers);
        registerPacketHandler("delete_entity", new DeleteEntityPacketListener(this), msg, LargePayloadBehaviour.DEFAULT, largePayloadHandlers);
        registerPacketHandler("marker_nbt_request", new MarkerNbtRequestPacketListener(this), msg, LargePayloadBehaviour.FORCE_SMALL, largePayloadHandlers);

        registerPacketHandler("set_block", new SetBlockPacketListener(this), msg, LargePayloadBehaviour.DEFAULT, largePayloadHandlers);
        registerPacketHandler("set_buffer", new SetBlockBufferPacketListener(this), msg, LargePayloadBehaviour.FORCE_LARGE, largePayloadHandlers);

        if (allowServerBlueprints) {
            registerPacketHandler("upload_blueprint", new UploadBlueprintPacketListener(this), msg, LargePayloadBehaviour.FORCE_LARGE, largePayloadHandlers);
            registerPacketHandler("request_blueprint", new BlueprintRequestPacketListener(this), msg, LargePayloadBehaviour.FORCE_SMALL, largePayloadHandlers);
        }
        if (this.allowAnnotations) {
            registerPacketHandler("annotation_update", new UpdateAnnotationPacketListener(this), msg, LargePayloadBehaviour.FORCE_LARGE, largePayloadHandlers);
        }

        if (!largePayloadHandlers.isEmpty()) {
            // Hack to figure out the id of the CustomPayload packet
            ProtocolInfo<ServerGamePacketListener> protocol = GameProtocols.SERVERBOUND_TEMPLATE.bind(k -> new RegistryFriendlyByteBuf(k,
                MinecraftServer.getServer().registryAccess()), new GameProtocols.Context() {
                @Override
                public boolean hasInfiniteMaterials() {
                    return false;
                }
            });
            RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), MinecraftServer.getServer().registryAccess());
            protocol.codec().encode(friendlyByteBuf, new ServerboundCustomPayloadPacket(VersionHelper.createCustomPayload(VersionHelper.createResourceLocation("dummy"), new byte[0])));
            int payloadId = friendlyByteBuf.readVarInt();

            ChannelInitializeListenerHolder.addListener(Key.key("axiom:handle_big_payload"), new ChannelInitializeListener() {
                @Override
                public void afterInitChannel(@NonNull Channel channel) {
                    Connection connection = (Connection) channel.pipeline().get("packet_handler");
                    AxiomBigPayloadHandler.apply(channel.pipeline(), new AxiomBigPayloadHandler(payloadId, connection, largePayloadHandlers));
                }
            });
        }

        if (allowServerBlueprints) {
            this.blueprintFolder = this.getDataFolder().toPath().resolve("blueprints");
            try {
                Files.createDirectories(this.blueprintFolder);
            } catch (IOException ignored) {}
            ServerBlueprintManager.initialize(this.blueprintFolder);
        }

        Path heightmapsPath = this.getDataFolder().toPath().resolve("heightmaps");
        try {
            Files.createDirectories(heightmapsPath);
        } catch (IOException ignored) {}
        ServerHeightmaps.load(heightmapsPath);

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::tick, 1, 1);

        this.sendMarkers = this.configuration.getBoolean("send-markers");
        this.maxChunkRelightsPerTick = this.configuration.getInt("max-chunk-relights-per-tick");
        this.maxChunkSendsPerTick = this.configuration.getInt("max-chunk-sends-per-tick");
        this.maxChunkLoadDistance = this.configuration.getInt("max-chunk-load-distance");

        this.logCoreProtectChanges = this.configuration.getBoolean("log-core-protect-changes");

        this.allowedDispatchSendsPerSecond = configuration.getInt("block-buffer-rate-limit");
        if (this.allowedDispatchSendsPerSecond <= 0) {
            this.allowedDispatchSendsPerSecond = 1024;
        }

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

    public int getMaxChunkLoadDistance(World world) {
        int maxChunkLoadDistance = this.maxChunkLoadDistance;

        // Don't allow loading chunks outside render distance for plot worlds
        if (PlotSquaredIntegration.isPlotWorld(world)) {
            maxChunkLoadDistance = 0;
        }

        return maxChunkLoadDistance;
    }

    private enum LargePayloadBehaviour {
        DEFAULT,
        FORCE_LARGE,
        FORCE_SMALL
    }

    private void tick() {
        Set<UUID> stillActiveAxiomPlayers = new HashSet<>();
        Set<UUID> stillFailedAxiomPlayers = new HashSet<>();

        // Clear cached permissions
        this.playerPermissions.clear();

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (this.activeAxiomPlayers.contains(uuid)) {
                if (!this.hasPermission(player, AxiomPermission.USE)) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBoolean(false);
                    byte[] bytes = ByteBufUtil.getBytes(buf);
                    VersionHelper.sendCustomPayload(player, "axiom:enable", bytes);

                    this.failedPermissionAxiomPlayers.add(uuid);
                } else {
                    stillActiveAxiomPlayers.add(uuid);
                    tickPlayer(player);
                }
            } else if (this.failedPermissionAxiomPlayers.contains(uuid)) {
                if (this.hasPermission(player, AxiomPermission.USE)) {
                    VersionHelper.sendCustomPayload(player, "axiom:redo_handshake", new byte[]{});
                    this.failedPermissionAxiomPlayers.remove(uuid);
                } else {
                    stillFailedAxiomPlayers.add(uuid);
                }
            }
        }

        this.activeAxiomPlayers.retainAll(stillActiveAxiomPlayers);
        this.availableDispatchSends.keySet().retainAll(stillActiveAxiomPlayers);
        this.playerRestrictions.keySet().retainAll(stillActiveAxiomPlayers);
        this.playerBlockRegistry.keySet().retainAll(stillActiveAxiomPlayers);
        this.playerProtocolVersion.keySet().retainAll(stillActiveAxiomPlayers);
        this.lastPlotBoundsForPlayers.keySet().retainAll(stillActiveAxiomPlayers);
        this.noPhysicalTriggerPlayers.retainAll(stillActiveAxiomPlayers);

        this.failedPermissionAxiomPlayers.retainAll(stillFailedAxiomPlayers);

        this.operationQueue.tick();

        WorldExtension.tick(MinecraftServer.getServer(), this.sendMarkers, this.maxChunkRelightsPerTick, this.maxChunkSendsPerTick);

        ImplServerCustomBlocks.tick();
        ImplServerCustomDisplays.tick();
        ImplAxiomHiddenEntities.tick();
    }

    public void addPendingOperation(ServerLevel level, PendingOperation operation) {
        this.operationQueue.add(level, operation);
    }

    public boolean consumeDispatchSends(Player player, int sends, int clientAvailableDispatchSends) {
        int currentSends = this.availableDispatchSends.getOrDefault(player.getUniqueId(), this.allowedDispatchSendsPerSecond*20);
        currentSends -= sends*20;
        currentSends = Math.min(currentSends, clientAvailableDispatchSends*20);
        this.availableDispatchSends.put(player.getUniqueId(), currentSends);

        if (currentSends < -this.allowedDispatchSendsPerSecond*20) {
            player.kick(net.kyori.adventure.text.Component.text("You are sending updates too fast!"));
            return false;
        } else {
            return true;
        }
    }

    public void tickPlayer(Player player) {
        if (!this.availableDispatchSends.containsKey(player.getUniqueId())) {
            this.availableDispatchSends.put(player.getUniqueId(), this.allowedDispatchSendsPerSecond*20);
            sendUpdateAvailableDispatchSends(player, this.allowedDispatchSendsPerSecond, this.allowedDispatchSendsPerSecond);
        } else {
            int previousAllowed20 = this.availableDispatchSends.getInt(player.getUniqueId());
            int newAllowed20 = Math.min(this.allowedDispatchSendsPerSecond*20, previousAllowed20 + this.allowedDispatchSendsPerSecond);
            this.availableDispatchSends.put(player.getUniqueId(), newAllowed20);

            int previousAllowed = previousAllowed20 / 20;
            int newAllowed = newAllowed20 / 20;
            if (previousAllowed != newAllowed) {
                sendUpdateAvailableDispatchSends(player, newAllowed - previousAllowed, this.allowedDispatchSendsPerSecond);
            }
        }

        Restrictions restrictions = this.calculateRestrictions(player);

        boolean restrictionsChanged;

        if (this.playerRestrictions.containsKey(player.getUniqueId())) {
            Restrictions oldRestrictions = this.playerRestrictions.get(player.getUniqueId());
            restrictionsChanged = !Objects.equals(restrictions, oldRestrictions);
        } else {
            restrictionsChanged = true;
        }

        if (restrictionsChanged) {
            restrictions.send(player);
            this.playerRestrictions.put(player.getUniqueId(), restrictions);
        }
    }

    private void sendUpdateAvailableDispatchSends(Player player, int add, int max) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(add);
        buf.writeVarInt(max);
        byte[] bytes = ByteBufUtil.getBytes(buf);
        VersionHelper.sendCustomPayload(player, "axiom:update_available_dispatch_sends", bytes);
    }

    private Restrictions calculateRestrictions(Player player) {
        if (player.isOp() || player.hasPermission("axiom.all")) {
            Restrictions restrictions = new Restrictions();
            restrictions.allowedPermissions = EnumSet.of(AxiomPermission.ALL);
            restrictions.infiniteReachLimit = this.infiniteReachLimit;
            return restrictions;
        }

        AxiomPermissionSet permissionSet = this.getPermissions(player);

        if (permissionSet.contains(AxiomPermission.ALL)) {
            Restrictions restrictions = new Restrictions();
            restrictions.allowedPermissions = EnumSet.of(AxiomPermission.ALL);
            restrictions.infiniteReachLimit = this.infiniteReachLimit;
            return restrictions;
        }

        Set<PlotSquaredIntegration.PlotBox> bounds = Set.of();

        if (!permissionSet.contains(AxiomPermission.ALLOW_COPYING_OTHER_PLOTS)) {
            if (PlotSquaredIntegration.isPlotWorld(player.getWorld())) {
                PlotSquaredIntegration.PlotBounds editable = PlotSquaredIntegration.getCurrentEditablePlot(player);
                if (editable != null) {
                    lastPlotBoundsForPlayers.put(player.getUniqueId(), editable);
                    bounds = editable.boxes();
                } else {
                    PlotSquaredIntegration.PlotBounds lastPlotBounds = lastPlotBoundsForPlayers.get(player.getUniqueId());
                    if (lastPlotBounds != null && lastPlotBounds.worldName().equals(player.getWorld().getName())) {
                        bounds = lastPlotBounds.boxes();
                    } else {
                        bounds = Set.of(new PlotSquaredIntegration.PlotBox(BlockPos.ZERO, BlockPos.ZERO));
                    }
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

        EnumSet<AxiomPermission> allowed = EnumSet.noneOf(AxiomPermission.class);
        EnumSet<AxiomPermission> denied = EnumSet.noneOf(AxiomPermission.class);

        for (AxiomPermission permission : permissionSet.explicitlyAllowed) {
            if (permission.parent != null && permissionSet.explicitlyAllowed.contains(permission.parent)) {
                continue;
            }
            allowed.add(permission);
        }
        for (AxiomPermission permission : permissionSet.explicitlyDenied) {
            if (permission.parent != null && permissionSet.explicitlyDenied.contains(permission.parent)) {
                continue;
            }
            denied.add(permission);
        }

        Restrictions restrictions = new Restrictions();
        restrictions.allowedPermissions = allowed;
        restrictions.deniedPermissions = denied;
        restrictions.infiniteReachLimit = this.infiniteReachLimit;
        restrictions.bounds = bounds;
        return restrictions;
    }

    private void registerPacketHandler(String name, PacketHandler handler, Messenger messenger, LargePayloadBehaviour behaviour,
                                       Map<String, PacketHandler> largePayloadHandlers) {
        boolean isLargePayload = switch (behaviour) {
            case DEFAULT -> this.configuration.getBoolean("allow-large-payload-for-all-packets");
            case FORCE_LARGE -> true;
            case FORCE_SMALL -> false;
        };

        if (isLargePayload) {
            largePayloadHandlers.put("axiom:"+name, handler);
            messenger.registerIncomingPluginChannel(this, "axiom:"+name, new DummyPacketListener());
        } else {
            messenger.registerIncomingPluginChannel(this, "axiom:"+name, new WrapperPacketListener(handler));
        }
    }

    public <T> IntFunction<T> limitCollection(IntFunction<T> applier) {
        return FriendlyByteBuf.limitValue(applier, this.packetCollectionReadLimit);
    }

    public NbtAccounter createNbtAccounter() {
        return NbtAccounter.create(this.maxNbtDecompressLimit);
    }

    public boolean logLargeBlockBufferChanges() {
        return this.logLargeBlockBufferChanges;
    }

    public boolean canUseAxiom(Player player) {
        return this.activeAxiomPlayers.contains(player.getUniqueId());
    }

    public boolean canUseAxiom(Player player, AxiomPermission axiomPermission) {
        return this.activeAxiomPlayers.contains(player.getUniqueId()) && hasPermission(player, axiomPermission);
    }

    public AxiomPermissionSet getPermissions(Player player) {
        return this.playerPermissions.computeIfAbsent(player.getUniqueId(), uuid -> {
            return this.calculatePermissions(player);
        });
    }

    public boolean hasPermission(Player player, AxiomPermission axiomPermission) {
        if (player.isOp()) {
            return true;
        }
        return this.getPermissions(player).contains(axiomPermission);
    }

    private AxiomPermissionSet calculatePermissions(Player player) {
        if (player.isOp()) {
            return AxiomPermissionSet.ALL;
        }

        EnumSet<AxiomPermission> allowed = EnumSet.noneOf(AxiomPermission.class);
        EnumSet<AxiomPermission> denied = EnumSet.noneOf(AxiomPermission.class);

        for (AxiomPermission permission : AxiomPermission.values()) {
            TriState value = player.permissionValue(permission.getPermissionNode());
            switch (value) {
                case FALSE -> denied.add(permission);
                case NOT_SET -> {
                }
                case TRUE -> allowed.add(permission);
            }
        }

        return new AxiomPermissionSet(allowed, denied);
    }

    public boolean canEntityBeManipulated(EntityType<?> entityType) {
        if (entityType == EntityType.PLAYER) {
            return false;
        }
        if (!this.whitelistedEntities.isEmpty() && !this.whitelistedEntities.contains(entityType)) {
            return false;
        }
        if (this.blacklistedEntities.contains(entityType)) {
            return false;
        }
        return true;
    }

    public boolean isNoPhysicalTrigger(UUID uuid) {
        return this.noPhysicalTriggerPlayers.contains(uuid);
    }

    public void setNoPhysicalTrigger(UUID uuid, boolean noPhysicalTrigger) {
        if (noPhysicalTrigger) {
            if (!this.registeredNoPhysicalTriggerListener) {
                this.registeredNoPhysicalTriggerListener = true;
                Bukkit.getPluginManager().registerEvents(new NoPhysicalTriggerListener(this), this);
            }

            this.noPhysicalTriggerPlayers.add(uuid);
        } else {
            this.noPhysicalTriggerPlayers.remove(uuid);
        }
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
    public void onPluginUnload(PluginDisableEvent disableEvent) {
        ImplServerCustomBlocks.unregisterAll(disableEvent.getPlugin());
        ImplServerCustomDisplays.unregisterAll(disableEvent.getPlugin());
    }

    @EventHandler
    public void onFailMove(PlayerFailMoveEvent event) {
        if (!this.canUseAxiom(event.getPlayer(), AxiomPermission.PLAYER_BYPASS_MOVEMENT_RESTRICTIONS)) {
            return;
        }
        if (event.getFailReason() == PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK) {
            return;
        }
        if (!event.getPlayer().getWorld().isChunkLoaded(event.getTo().getBlockX() >> 4, event.getTo().getBlockZ() >> 4)) {
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
            VersionHelper.sendCustomPayload(event.getPlayer(), "axiom:register_world_properties", new byte[]{0});
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
