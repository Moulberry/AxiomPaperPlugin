package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.annotations.AnnotationUpdateAction;
import com.moulberry.axiom.annotations.ServerAnnotations;
import com.moulberry.axiom.blueprint.BlueprintIo;
import com.moulberry.axiom.blueprint.RawBlueprint;
import com.moulberry.axiom.blueprint.ServerBlueprintManager;
import com.moulberry.axiom.blueprint.ServerBlueprintRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UpdateAnnotationPacketListener {

    private final AxiomPaper plugin;
    public UpdateAnnotationPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    public void onReceive(ServerPlayer serverPlayer, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.allowAnnotations || !this.plugin.canUseAxiom(serverPlayer.getBukkitEntity(), "axiom.annotation.create")) {
            friendlyByteBuf.writerIndex(friendlyByteBuf.readerIndex());
            return;
        }

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
