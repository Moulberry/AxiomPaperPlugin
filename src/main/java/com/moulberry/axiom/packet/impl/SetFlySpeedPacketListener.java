package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomFlySpeedChangeEvent;
import com.moulberry.axiom.packet.PacketHandler;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SetFlySpeedPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SetFlySpeedPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, "axiom.player.speed")) {
            return;
        }

        float flySpeed = friendlyByteBuf.readFloat();

        // Call event
        AxiomFlySpeedChangeEvent flySpeedChangeEvent = new AxiomFlySpeedChangeEvent(player, flySpeed);
        Bukkit.getPluginManager().callEvent(flySpeedChangeEvent);
        if (flySpeedChangeEvent.isCancelled()) return;

        // Change flying speed
        ((CraftPlayer)player).getHandle().getAbilities().setFlyingSpeed(flySpeed);
    }

}
