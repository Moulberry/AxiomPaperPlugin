package com.moulberry.axiom.packet;

import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class WrapperPacketListener implements PluginMessageListener {

    private final PacketHandler packetHandler;

    public WrapperPacketListener(PacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String s, @NotNull Player player, @NotNull byte[] bytes) {
        RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), ((CraftPlayer)player).getHandle().registryAccess());
        try {
            this.packetHandler.onReceive(player, friendlyByteBuf);
        } catch (Throwable t) {
            player.kick(Component.text("Error while processing packet " + s + ": " + t.getMessage()));
        }
    }
}
