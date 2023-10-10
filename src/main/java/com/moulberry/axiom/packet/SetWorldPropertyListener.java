package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.world_properties.server.ServerWorldPropertiesRegistry;
import com.moulberry.axiom.world_properties.server.ServerWorldProperty;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class SetWorldPropertyListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!player.hasPermission("axiom.*")) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        ResourceLocation id = friendlyByteBuf.readResourceLocation();
        int type = friendlyByteBuf.readVarInt();
        byte[] data = friendlyByteBuf.readByteArray();
        int updateId = friendlyByteBuf.readVarInt();

        ServerWorldPropertiesRegistry registry = AxiomPaper.PLUGIN.getWorldProperties(player.getWorld());
        if (registry == null) return;

        ServerWorldProperty<?> property = registry.getById(id);
        if (property != null && property.getType().getTypeId() == type) {
            property.update(player.getWorld(), data);
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(updateId);

        byte[] bytes = new byte[buf.writerIndex()];
        buf.getBytes(0, bytes);
        player.sendPluginMessage(AxiomPaper.PLUGIN, "axiom:ack_world_properties", bytes);
    }

}
