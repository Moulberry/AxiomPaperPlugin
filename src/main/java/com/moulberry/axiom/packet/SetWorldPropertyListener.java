package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertyHolder;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SetWorldPropertyListener implements PluginMessageListener {

    private final AxiomPaper plugin;
    public SetWorldPropertyListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!this.plugin.canUseAxiom(player)) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        ResourceLocation id = friendlyByteBuf.readResourceLocation();
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
            property.update(player, player.getWorld(), data);
        }

        sendAck(player, updateId);
    }

    private void sendAck(Player player, int updateId) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(updateId);

        byte[] bytes = new byte[buf.writerIndex()];
        buf.getBytes(0, bytes);
        player.sendPluginMessage(AxiomPaper.PLUGIN, "axiom:ack_world_properties", bytes);
    }

}
