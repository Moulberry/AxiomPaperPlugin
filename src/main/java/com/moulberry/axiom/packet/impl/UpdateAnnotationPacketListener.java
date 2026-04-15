package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.annotations.AnnotationUpdateAction;
import com.moulberry.axiom.annotations.ServerAnnotations;
import com.moulberry.axiom.integration.prism.PrismAxiomIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.allowAnnotations || !this.plugin.canUseAxiom(player, AxiomPermission.ANNOTATION_CREATE)) {
            friendlyByteBuf.writerIndex(friendlyByteBuf.readerIndex());
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        CraftPlayer craftPlayer = (CraftPlayer) player;
        World world = player.getWorld();

        // Read actions
        int length = friendlyByteBuf.readVarInt();
        List<AnnotationUpdateAction> actions = new ArrayList<>(Math.min(256, length));
        for (int i = 0; i < length; i++) {
            AnnotationUpdateAction action = AnnotationUpdateAction.read(friendlyByteBuf);
            if (action != null) {
                actions.add(action);
            }
        }

        // Execute
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
                byte[] oldSnapshot = ServerAnnotations.createSnapshot(world);
                ServerAnnotations.handleUpdates(world, actions);
                PrismAxiomIntegration.logAnnotationSnapshot(craftPlayer, world, oldSnapshot, ServerAnnotations.createSnapshot(world));
            } catch (Throwable t) {
                craftPlayer.kick(net.kyori.adventure.text.Component.text(
                        "An error occured while updating annotations: " + t.getMessage()));
            }
        });
    }

}
