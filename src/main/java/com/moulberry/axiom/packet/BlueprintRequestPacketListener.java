package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.blueprint.RawBlueprint;
import com.moulberry.axiom.blueprint.ServerBlueprintManager;
import com.moulberry.axiom.blueprint.ServerBlueprintRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class BlueprintRequestPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;
    public BlueprintRequestPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    private static final ResourceLocation RESPONSE_PACKET_IDENTIFIER = new ResourceLocation("axiom:response_blueprint");

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!this.plugin.canUseAxiom(player)) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
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
