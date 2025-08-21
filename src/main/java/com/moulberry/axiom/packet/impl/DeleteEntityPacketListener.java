package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomRemoveEntityEvent;
import com.moulberry.axiom.event.AxiomSpawnEntityEvent;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeleteEntityPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public DeleteEntityPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.ENTITY_DELETE)) {
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        List<UUID> delete = friendlyByteBuf.readCollection(this.plugin.limitCollection(ArrayList::new), buf -> buf.readUUID());

        ServerLevel serverLevel = ((CraftWorld)player.getWorld()).getHandle();

        for (UUID uuid : delete) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity == null || entity instanceof net.minecraft.world.entity.player.Player || entity.hasPassenger(e -> e instanceof net.minecraft.world.entity.player.Player)) continue;

            if (!this.plugin.canEntityBeManipulated(entity.getType())) {
                continue;
            }

            if (!Integration.canBreakBlock(player,
                    player.getWorld().getBlockAt(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()))) {
                continue;
            }


            AxiomRemoveEntityEvent removeEntityEvent = new AxiomRemoveEntityEvent(player, entity.getBukkitEntity());
            Bukkit.getPluginManager().callEvent(removeEntityEvent);

            if (!removeEntityEvent.isCancelled()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }

}
