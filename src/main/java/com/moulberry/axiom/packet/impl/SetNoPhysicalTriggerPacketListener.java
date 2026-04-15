package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.integration.prism.PrismAxiomIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.bukkit.entity.Player;

public class SetNoPhysicalTriggerPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SetNoPhysicalTriggerPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.PLAYER_SETNOPHYSICALTRIGGER)) {
            return;
        }

        boolean oldValue = this.plugin.isNoPhysicalTrigger(player.getUniqueId());
        boolean newValue = friendlyByteBuf.readBoolean();
        this.plugin.setNoPhysicalTrigger(player.getUniqueId(), newValue);
        PrismAxiomIntegration.logPlayerNoPhysicalTrigger(player, player, oldValue, newValue);
    }

}
