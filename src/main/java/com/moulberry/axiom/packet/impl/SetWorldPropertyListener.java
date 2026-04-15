package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.integration.prism.PrismAxiomIntegration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertyHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.bukkit.entity.Player;

public class SetWorldPropertyListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SetWorldPropertyListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.WORLD_PROPERTY)) {
            return;
        }

        Identifier id = friendlyByteBuf.readIdentifier();
        int type = friendlyByteBuf.readVarInt();
        byte[] data = friendlyByteBuf.readByteArray();
        int updateId = friendlyByteBuf.readVarInt();

        // Call modify world
        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            sendAck(player, updateId);
            return;
        }

        // Don't allow on plot worlds
        if (PlotSquaredIntegration.isPlotWorld(player.getWorld())) {
            sendAck(player, updateId);
            return;
        }

        ServerWorldPropertiesRegistry registry = AxiomPaper.PLUGIN.getOrCreateWorldProperties(player.getWorld());
        if (registry == null) {
            sendAck(player, updateId);
            return;
        }

        ServerWorldPropertyHolder<?> property = registry.getById(id);
        if (property != null && property.getType().getTypeId() == type) {
            byte[] oldValue = property.serializeValue();
            property.update(player, player.getWorld(), data);
            PrismAxiomIntegration.logWorldPropertyChange(player, player.getWorld(), id.toString(), oldValue, property.serializeValue());
        }

        sendAck(player, updateId);
    }

    private void sendAck(Player player, int updateId) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(updateId);

        byte[] bytes = ByteBufUtil.getBytes(buf);
        VersionHelper.sendCustomPayload(player, "axiom:ack_world_properties", bytes);
    }

}
