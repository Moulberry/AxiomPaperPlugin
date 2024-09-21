package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.blueprint.RawBlueprint;
import com.moulberry.axiom.blueprint.ServerBlueprintManager;
import com.moulberry.axiom.blueprint.ServerBlueprintRegistry;
import com.moulberry.axiom.packet.PacketHandler;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class BlueprintRequestPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public BlueprintRequestPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    private static final ResourceLocation RESPONSE_PACKET_IDENTIFIER = VersionHelper.createResourceLocation("axiom:response_blueprint");

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, "axiom.blueprint.request")) {
            return;
        }

        if (this.plugin.isMismatchedDataVersion(player.getUniqueId())) {
            player.sendMessage(Component.text("Axiom+ViaVersion: This feature isn't supported. Switch your client version to " + SharedConstants.VERSION_STRING + " to use this"));
            return;
        }

        String path = friendlyByteBuf.readUtf();

        ServerBlueprintRegistry registry = ServerBlueprintManager.getRegistry();
        if (registry == null) {
            return;
        }

        RawBlueprint rawBlueprint = registry.blueprints().get(path);
        if (rawBlueprint != null) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

            buf.writeUtf(path);
            RawBlueprint.write(buf, rawBlueprint);

            byte[] bytes = new byte[buf.writerIndex()];
            buf.getBytes(0, bytes);
            VersionHelper.sendCustomPayload(((CraftPlayer)player).getHandle(), RESPONSE_PACKET_IDENTIFIER, bytes);
        }
    }

}
