package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.annotations.AnnotationUpdateAction;
import com.moulberry.axiom.annotations.ServerAnnotations;
import com.moulberry.axiom.packet.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class UpdateAnnotationPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public UpdateAnnotationPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.allowAnnotations || !this.plugin.canUseAxiom(player, "axiom.annotation.create")) {
            friendlyByteBuf.writerIndex(friendlyByteBuf.readerIndex());
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        ServerPlayer serverPlayer = ((CraftPlayer)player).getHandle();

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
        serverPlayer.getServer().execute(() -> {
            try {
                ServerAnnotations.handleUpdates(serverPlayer.serverLevel().getWorld(), actions);
            } catch (Throwable t) {
                serverPlayer.getBukkitEntity().kick(net.kyori.adventure.text.Component.text(
                        "An error occured while updating annotations: " + t.getMessage()));
            }
        });
    }

}
