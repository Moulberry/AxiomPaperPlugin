package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomGameModeChangeEvent;
import com.moulberry.axiom.integration.prism.PrismAxiomIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class SetGamemodePacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SetGamemodePacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        GameType gameType = GameType.byId(friendlyByteBuf.readByte());

        AxiomPermission permission = switch (gameType) {
            case SURVIVAL -> AxiomPermission.PLAYER_GAMEMODE_SURVIVAL;
            case CREATIVE -> AxiomPermission.PLAYER_GAMEMODE_CREATIVE;
            case ADVENTURE -> AxiomPermission.PLAYER_GAMEMODE_ADVENTURE;
            case SPECTATOR -> AxiomPermission.PLAYER_GAMEMODE_SPECTATOR;
        };

        if (!this.plugin.canUseAxiom(player, permission)) {
            return;
        }

        // Call event
        AxiomGameModeChangeEvent gameModeChangeEvent = new AxiomGameModeChangeEvent(player, toBukkitGameMode(gameType));
        Bukkit.getPluginManager().callEvent(gameModeChangeEvent);
        if (gameModeChangeEvent.isCancelled()) return;

        // Change gamemode
        GameMode oldMode = player.getGameMode();
        ((CraftPlayer)player).getHandle().setGameMode(gameType);
        PrismAxiomIntegration.logPlayerGamemode(player, player, oldMode, player.getGameMode());
    }

    private static GameMode toBukkitGameMode(GameType gameType) {
        return switch (gameType) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
        };
    }

}
