package com.moulberry.axiom.paperapi.display;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.paperapi.AxiomAlreadyRegisteredException;
import com.moulberry.axiom.paperapi.block.AxiomCustomBlockBuilder;
import com.moulberry.axiom.paperapi.block.AxiomProperty;
import com.moulberry.axiom.paperapi.block.ImplAxiomCustomBlock;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public class ImplServerCustomDisplays {

    private static final Map<ResourceLocation, ImplAxiomCustomDisplay> registeredDisplays = new LinkedHashMap<>();
    private static final Map<Plugin, List<ResourceLocation>> byPlugin = new HashMap<>();
    private static boolean pendingReregisterAll = false;
    private static boolean hasRegisteredToAPlayer = false;

    public static void register(Plugin plugin, AxiomCustomDisplayBuilder customDisplayBuilder) throws AxiomAlreadyRegisteredException {
        if (!MinecraftServer.getServer().isSameThread()) {
            throw new WrongThreadException();
        }

        ImplAxiomCustomDisplay customDisplay = customDisplayBuilder.build();

        // Check for duplicate registration
        if (registeredDisplays.containsKey(customDisplay.id())) {
            throw new AxiomAlreadyRegisteredException("Custom display is already registered with id " + customDisplay.id());
        }

        // Register
        registeredDisplays.put(customDisplay.id(), customDisplay);
        byPlugin.computeIfAbsent(plugin, k -> new ArrayList<>()).add(customDisplay.id());

        // Send
        if (!pendingReregisterAll && hasRegisteredToAPlayer) {
            List<ServerPlayer> players = new ArrayList<>();
            for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
                if (AxiomPaper.PLUGIN.canUseAxiom(player.getBukkitEntity())) {
                    int playerProtocolVersion = AxiomPaper.PLUGIN.getProtocolVersionFor(player.getUUID());
                    if (playerProtocolVersion == SharedConstants.getProtocolVersion()) {
                        players.add(player);
                    }
                }
            }
            if (!players.isEmpty()) {
                var registryAccess = MinecraftServer.getServer().registryAccess();
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
                write(buf);
                byte[] bytes = ByteBufUtil.getBytes(buf);
                VersionHelper.sendCustomPayloadToAll(players, "axiom:register_custom_items", bytes);
            }
        }
    }

    private static void write(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
        registryFriendlyByteBuf.writeVarInt(registeredDisplays.size());
        for (ImplAxiomCustomDisplay value : registeredDisplays.values()) {
            value.write(registryFriendlyByteBuf);
        }
    }

    public static void unregisterAll(Plugin plugin) {
        List<ResourceLocation> remove = byPlugin.remove(plugin);
        if (remove == null || remove.isEmpty()) {
            return;
        }

        if (hasRegisteredToAPlayer) {
            pendingReregisterAll = true;
        }

        for (ResourceLocation id : remove) {
            registeredDisplays.remove(id);
        }
    }

    public static void tick() {
        if (pendingReregisterAll) {
            pendingReregisterAll = false;

            List<ServerPlayer> players = new ArrayList<>();

            for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
                if (AxiomPaper.PLUGIN.canUseAxiom(player.getBukkitEntity())) {
                    int playerProtocolVersion = AxiomPaper.PLUGIN.getProtocolVersionFor(player.getUUID());
                    if (playerProtocolVersion == SharedConstants.getProtocolVersion()) {
                        players.add(player);
                    }
                }
            }

            if (players.isEmpty()) {
                hasRegisteredToAPlayer = false;
            } else {
                var registryAccess = MinecraftServer.getServer().registryAccess();
                sendAll(players, registryAccess);
            }
        }
    }

    private static void sendAll(List<ServerPlayer> players, RegistryAccess registryAccess) {
        if (players.isEmpty()) {
            return;
        }

        hasRegisteredToAPlayer = true;

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        write(buf);
        VersionHelper.sendCustomPayloadToAll(players, "axiom:register_custom_items", ByteBufUtil.getBytes(buf));
    }

    public static void sendAll(ServerPlayer player) {
        hasRegisteredToAPlayer = true;

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        write(buf);
        VersionHelper.sendCustomPayload(player, "axiom:register_custom_items", ByteBufUtil.getBytes(buf));
    }

}
