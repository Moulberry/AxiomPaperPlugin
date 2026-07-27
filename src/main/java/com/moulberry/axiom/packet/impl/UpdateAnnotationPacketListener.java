package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.annotations.AnnotationUpdateAction;
import com.moulberry.axiom.annotations.ServerAnnotations;
import com.moulberry.axiom.integration.prism.PrismIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class UpdateAnnotationPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public UpdateAnnotationPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.allowAnnotations || !this.plugin.canUseAxiom(player)) {
            friendlyByteBuf.writerIndex(friendlyByteBuf.readerIndex());
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        CraftPlayer craftPlayer = (CraftPlayer) player;
        World world = player.getWorld();

        // Read actions
        List<AnnotationUpdateAction> actions = friendlyByteBuf.readCollection(
            this.plugin.limitCollection(ArrayList::new),
            AnnotationUpdateAction::read
        );
        if (actions.stream().anyMatch(java.util.Objects::isNull)) {
            return;
        }
        boolean clearsAll = actions.stream().anyMatch(AnnotationUpdateAction.ClearAllAnnotations.class::isInstance);
        if (clearsAll && !this.plugin.hasPermission(player, AxiomPermission.ANNOTATION_CLEARALL)) {
            return;
        }
        if (actions.stream().anyMatch(action -> !(action instanceof AnnotationUpdateAction.ClearAllAnnotations))
            && !this.plugin.hasPermission(player, AxiomPermission.ANNOTATION_CREATE)) {
            return;
        }

        // Execute
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
                var oldSnapshot = ServerAnnotations.captureSnapshot(world);
                ServerAnnotations.handleUpdates(world, actions);
                var newSnapshot = ServerAnnotations.captureSnapshot(world);
                PrismIntegration.logAnnotationChange(
                    craftPlayer,
                    world,
                    ServerAnnotations.createDelta(newSnapshot, oldSnapshot),
                    ServerAnnotations.createDelta(oldSnapshot, newSnapshot)
                );
            } catch (Throwable t) {
                craftPlayer.kick(net.kyori.adventure.text.Component.text(
                        "An error occured while updating annotations: " + t.getMessage()));
            }
        });
    }

}
