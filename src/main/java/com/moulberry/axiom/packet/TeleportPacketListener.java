package com.moulberry.axiom.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class TeleportPacketListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
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
    }

}
