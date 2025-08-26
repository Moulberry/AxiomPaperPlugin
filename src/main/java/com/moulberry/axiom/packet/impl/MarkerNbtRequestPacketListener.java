package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.marker.MarkerData;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MarkerNbtRequestPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public MarkerNbtRequestPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.ENTITY_REQUESTDATA)) {
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        UUID uuid = friendlyByteBuf.readUUID();
        int reason = friendlyByteBuf.readVarInt();

        ServerLevel serverLevel = ((CraftWorld)player.getWorld()).getHandle();

        Entity entity = serverLevel.getEntity(uuid);
        if (entity instanceof Marker marker) {
            CompoundTag data = MarkerData.getData(marker);

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUUID(uuid);
            buf.writeNbt(data);

            byte[] bytes = ByteBufUtil.getBytes(buf);
            VersionHelper.sendCustomPayload(player, "axiom:marker_nbt_response", bytes);
        }
    }

}
