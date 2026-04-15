package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomUnknownTeleportEvent;
import com.moulberry.axiom.event.AxiomTeleportEvent;
import com.moulberry.axiom.integration.prism.PrismAxiomIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.bukkit.*;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.entity.Player;

public class TeleportPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public TeleportPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.PLAYER_TELEPORT)) {
            return;
        }

        ResourceKey<Level> resourceKey = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        double x = friendlyByteBuf.readDouble();
        double y = friendlyByteBuf.readDouble();
        double z = friendlyByteBuf.readDouble();
        float yRot = friendlyByteBuf.readFloat();
        float xRot = friendlyByteBuf.readFloat();
        NamespacedKey targetWorldKey = CraftNamespacedKey.fromMinecraft(resourceKey.identifier());

        // Prevent teleport based on config value
        boolean allowTeleportBetweenWorlds = this.plugin.configuration.getBoolean("allow-teleport-between-worlds");
        if (!allowTeleportBetweenWorlds && !player.getWorld().getKey().equals(targetWorldKey)) {
            return;
        }

        // Call unknown teleport event
        AxiomUnknownTeleportEvent preTeleportEvent = new AxiomUnknownTeleportEvent(player,
                targetWorldKey, x, y, z, yRot, xRot);
        Bukkit.getPluginManager().callEvent(preTeleportEvent);
        if (preTeleportEvent.isCancelled()) return;

        // Get bukkit world
        World world = Bukkit.getWorld(targetWorldKey);
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
        Location oldLocation = player.getLocation();
        Location newLocation = new Location(world, x, y, z, yRot, xRot);
        if (player.teleport(newLocation)) {
            PrismAxiomIntegration.logPlayerTeleport(player, player, oldLocation, newLocation);
        }
    }

}
