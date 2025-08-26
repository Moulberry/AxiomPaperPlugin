package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomGameModeChangeEvent;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SetGamemodePacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SetGamemodePacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        GameType gameType = GameType.byId(friendlyByteBuf.readByte());

        AxiomPermission permission = switch (gameType) {
            case SURVIVAL -> AxiomPermission.PLAYER_GAMEMODE_SURVIVAL;
            case CREATIVE -> AxiomPermission.PLAYER_GAMEMODE_CREATIVE;
            case ADVENTURE -> AxiomPermission.PLAYER_GAMEMODE_ADVENTURE;
            case SPECTATOR -> AxiomPermission.PLAYER_GAMEMODE_SPECTATOR;
            default -> AxiomPermission.PLAYER_GAMEMODE;
        };

        if (!this.plugin.canUseAxiom(player, permission)) {
            return;
        }

        // Call event
        AxiomGameModeChangeEvent gameModeChangeEvent = new AxiomGameModeChangeEvent(player, GameMode.getByValue(gameType.getId()));
        Bukkit.getPluginManager().callEvent(gameModeChangeEvent);
        if (gameModeChangeEvent.isCancelled()) return;

        // Change gamemode
        ((CraftPlayer)player).getHandle().setGameMode(gameType);
    }

}
