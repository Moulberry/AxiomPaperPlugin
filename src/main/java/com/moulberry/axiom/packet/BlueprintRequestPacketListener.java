package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.blueprint.RawBlueprint;
import com.moulberry.axiom.blueprint.ServerBlueprintManager;
import com.moulberry.axiom.blueprint.ServerBlueprintRegistry;
import com.moulberry.axiom.marker.MarkerData;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
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
            var payload = new CustomByteArrayPayload(RESPONSE_PACKET_IDENTIFIER, bytes);
            ((CraftPlayer)player).getHandle().connection.send(new ClientboundCustomPayloadPacket(payload));
        }
    }

}
