package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomTimeChangeEvent;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.entity.Player;

public class SetNoPhysicalTriggerPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SetNoPhysicalTriggerPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.PLAYER_SETNOPHYSICALTRIGGER)) {
            return;
        }

        this.plugin.setNoPhysicalTrigger(player.getUniqueId(), friendlyByteBuf.readBoolean());
    }

}
