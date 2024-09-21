package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomUnknownTeleportEvent;
import com.moulberry.axiom.event.AxiomTeleportEvent;
import com.moulberry.axiom.packet.PacketHandler;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftNamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class TeleportPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public TeleportPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, "axiom.world.teleport")) {
            return;
        }

        ResourceKey<Level> resourceKey = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        double x = friendlyByteBuf.readDouble();
        double y = friendlyByteBuf.readDouble();
        double z = friendlyByteBuf.readDouble();
        float yRot = friendlyByteBuf.readFloat();
        float xRot = friendlyByteBuf.readFloat();

        // Prevent teleport based on config value
        boolean allowTeleportBetweenWorlds = this.plugin.configuration.getBoolean("allow-teleport-between-worlds");
        if (!allowTeleportBetweenWorlds && !((CraftPlayer)player).getHandle().serverLevel().dimension().equals(resourceKey)) {
            return;
        }

        // Call unknown teleport event
        AxiomUnknownTeleportEvent preTeleportEvent = new AxiomUnknownTeleportEvent(player,
                CraftNamespacedKey.fromMinecraft(resourceKey.location()), x, y, z, yRot, xRot);
        Bukkit.getPluginManager().callEvent(preTeleportEvent);
        if (preTeleportEvent.isCancelled()) return;

        // Get bukkit world
        NamespacedKey namespacedKey = new NamespacedKey(resourceKey.location().getNamespace(), resourceKey.location().getPath());
        World world = Bukkit.getWorld(namespacedKey);
        if (world == null) return;

        // Prevent teleport based on config value
        if (!allowTeleportBetweenWorlds && world != player.getWorld()) {
            return;
        }

        // Call event
        AxiomTeleportEvent teleportEvent = new AxiomTeleportEvent(player, new Location(world, x, y, z, yRot, xRot));
        Bukkit.getPluginManager().callEvent(teleportEvent);
        if (teleportEvent.isCancelled()) return;

        // Do teleport
        player.teleport(new Location(world, x, y, z, yRot, xRot));
    }

}
