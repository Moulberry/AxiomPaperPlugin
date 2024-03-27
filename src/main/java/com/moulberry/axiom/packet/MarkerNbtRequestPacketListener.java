package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.marker.MarkerData;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MarkerNbtRequestPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;
    public MarkerNbtRequestPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!this.plugin.canUseAxiom(player)) {
            return;
        }

        if (!player.hasPermission("axiom.entity.*") && !player.hasPermission("axiom.entity.manipulate")) {
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        UUID uuid = friendlyByteBuf.readUUID();

        ServerLevel serverLevel = ((CraftWorld)player.getWorld()).getHandle();

        Entity entity = serverLevel.getEntity(uuid);
        if (entity instanceof Marker marker) {
            CompoundTag data = MarkerData.getData(marker);

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUUID(uuid);
            buf.writeNbt(data);
            byte[] bytes = new byte[buf.writerIndex()];
            buf.getBytes(0, bytes);

            player.sendPluginMessage(AxiomPaper.PLUGIN, "axiom:marker_nbt_response", bytes);
        }
    }

}
