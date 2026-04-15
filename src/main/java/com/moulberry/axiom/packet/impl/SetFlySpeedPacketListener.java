package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomFlySpeedChangeEvent;
import com.moulberry.axiom.integration.prism.PrismAxiomIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class SetFlySpeedPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SetFlySpeedPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.PLAYER_SPEED)) {
            return;
        }

        float flySpeed = friendlyByteBuf.readFloat();

        flySpeed = Mth.clamp(flySpeed, -1.0f, 1.0f);

        // Call event
        AxiomFlySpeedChangeEvent flySpeedChangeEvent = new AxiomFlySpeedChangeEvent(player, flySpeed);
        Bukkit.getPluginManager().callEvent(flySpeedChangeEvent);
        if (flySpeedChangeEvent.isCancelled()) return;

        // Change flying speed
        float oldSpeed = ((CraftPlayer)player).getHandle().getAbilities().getFlyingSpeed();
        ((CraftPlayer)player).getHandle().getAbilities().setFlyingSpeed(flySpeed);
        PrismAxiomIntegration.logPlayerFlySpeed(player, player, oldSpeed, flySpeed);
    }

}
