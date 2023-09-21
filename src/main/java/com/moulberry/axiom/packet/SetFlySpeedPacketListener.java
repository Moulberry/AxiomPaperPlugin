package com.moulberry.axiom.packet;

import com.moulberry.axiom.event.AxiomFlySpeedChangeEvent;
import com.moulberry.axiom.event.AxiomGameModeChangeEvent;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SetFlySpeedPacketListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!player.hasPermission("axiom.*")) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        float flySpeed = friendlyByteBuf.readFloat();

        // Call event
        AxiomFlySpeedChangeEvent flySpeedChangeEvent = new AxiomFlySpeedChangeEvent(player, flySpeed);
        Bukkit.getPluginManager().callEvent(flySpeedChangeEvent);
        if (flySpeedChangeEvent.isCancelled()) return;

        // Change flying speed
        ((CraftPlayer)player).getHandle().getAbilities().setFlyingSpeed(flySpeed);
    }

}
