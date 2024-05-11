package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomFlySpeedChangeEvent;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SetFlySpeedPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;
    public SetFlySpeedPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        try {
            this.process(player, message);
        } catch (Throwable t) {
            player.kick(Component.text("Error while processing packet " + channel + ": " + t.getMessage()));
        }
    }

    private void process(Player player, byte[] message) {
        if (!this.plugin.canUseAxiom(player, "axiom.player.speed")) {
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
