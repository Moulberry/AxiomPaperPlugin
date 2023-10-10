package com.moulberry.axiom.packet;

import com.moulberry.axiom.event.AxiomGameModeChangeEvent;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SetGamemodePacketListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!player.hasPermission("axiom.*")) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        GameType gameType = GameType.byId(friendlyByteBuf.readByte());

        // Call event
        AxiomGameModeChangeEvent gameModeChangeEvent = new AxiomGameModeChangeEvent(player, GameMode.getByValue(gameType.getId()));
        Bukkit.getPluginManager().callEvent(gameModeChangeEvent);
        if (gameModeChangeEvent.isCancelled()) return;

        // Change gamemode
        ((CraftPlayer)player).getHandle().setGameMode(gameType);
    }

}
