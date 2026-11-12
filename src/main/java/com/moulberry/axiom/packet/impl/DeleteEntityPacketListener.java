package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.FoliaSupport;
import com.moulberry.axiom.event.AxiomRemoveEntityEvent;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeleteEntityPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public DeleteEntityPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.ENTITY_DELETE)) {
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        List<UUID> delete = friendlyByteBuf.readCollection(this.plugin.limitCollection(ArrayList::new), buf -> buf.readUUID());

        ServerLevel serverLevel = ((CraftWorld)player.getWorld()).getHandle();

        World world = player.getWorld();

        for (UUID uuid : delete) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity == null || entity instanceof net.minecraft.world.entity.player.Player) continue;

            FoliaSupport.runForEntity(entity, () -> this.deleteEntity(player, world, entity));
        }
    }

    private void deleteEntity(Player player, World world, Entity entity) {
        if (entity.isRemoved() || entity.hasPassenger(e -> e instanceof net.minecraft.world.entity.player.Player)) return;

        if (!this.plugin.canEntityBeManipulated(entity.getType())) {
            return;
        }

        if (!Integration.canBreakBlock(player,
                world.getBlockAt(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()))) {
            return;
        }

        AxiomRemoveEntityEvent removeEntityEvent = new AxiomRemoveEntityEvent(player, entity.getBukkitEntity());
        Bukkit.getPluginManager().callEvent(removeEntityEvent);

        if (!removeEntityEvent.isCancelled()) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
    }

}
