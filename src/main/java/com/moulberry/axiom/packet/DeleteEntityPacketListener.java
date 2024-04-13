package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeleteEntityPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;
    public DeleteEntityPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!this.plugin.canUseAxiom(player)) {
            return;
        }

        if (!player.hasPermission("axiom.entity.*") && !player.hasPermission("axiom.entity.delete")) {
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        List<UUID> delete = friendlyByteBuf.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 1000),
            FriendlyByteBuf::readUUID);

        ServerLevel serverLevel = ((CraftWorld)player.getWorld()).getHandle();

        List<String> whitelistedEntities = this.plugin.configuration.getStringList("whitelist-entities");
        List<String> blacklistedEntities = this.plugin.configuration.getStringList("blacklist-entities");

        for (UUID uuid : delete) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity == null || entity instanceof net.minecraft.world.entity.player.Player || entity.hasPassenger(e -> e instanceof net.minecraft.world.entity.player.Player)) continue;

            String type = EntityType.getKey(entity.getType()).toString();

            if (!whitelistedEntities.isEmpty() && !whitelistedEntities.contains(type)) continue;
            if (blacklistedEntities.contains(type)) continue;

            if (!Integration.canBreakBlock(player,
                    player.getWorld().getBlockAt(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()))) {
                continue;
            }

            entity.remove(Entity.RemovalReason.DISCARDED);
        }
    }

}
