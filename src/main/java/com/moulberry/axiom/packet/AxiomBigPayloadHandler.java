package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.packet.impl.RequestChunkDataPacketListener;
import com.moulberry.axiom.packet.impl.SetBlockBufferPacketListener;
import com.moulberry.axiom.packet.impl.UpdateAnnotationPacketListener;
import com.moulberry.axiom.packet.impl.UploadBlueprintPacketListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.papermc.paper.connection.DisconnectionReason;
import io.papermc.paper.network.ConnectionEvent;
import net.kyori.adventure.text.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AxiomBigPayloadHandler extends MessageToMessageDecoder<ByteBuf> {

    private final int payloadId;
    private final Connection connection;
    private final Map<String, PacketHandler> packetHandlers;
    private boolean handleUserEvents;

    public AxiomBigPayloadHandler(int payloadId, Connection connection, Map<String, PacketHandler> packetHandlers, boolean handleUserEvents) {
        this.payloadId = payloadId;
        this.connection = connection;
        this.packetHandlers = packetHandlers;
        this.handleUserEvents = handleUserEvents;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Skip if no readable bytes or inactive channel
        if (in.readableBytes() == 0 || !ctx.channel().isActive()) {
            out.add(in.retain());
            return;
        }

        // Don't handle if player doesn't have permission to use Axiom
        ServerPlayer player = this.connection.getPlayer();
        if (player == null || player.hasDisconnected() || !AxiomPaper.PLUGIN.canUseAxiom(player.getBukkitEntity())) {
            out.add(in.retain());
            return;
        }

        int readerIndex = in.readerIndex();
        boolean success = false;
        boolean allowIndexOutOfBounds = true;
        try {
            int packetId = VarInt.read(in);

            if (packetId == this.payloadId) {
                String identifier = Utf8String.read(in, 32767);
                allowIndexOutOfBounds = false;

                PacketHandler handler = this.packetHandlers.get(identifier);
                if (handler != null) {
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(in, player.registryAccess());
                    if (handler.handleAsync()) {
                        callReceive(handler, player, buf, identifier);
                    } else {
                        byte[] bytes = ByteBufUtil.getBytes(buf);

                        player.getServer().execute(() -> {
                            RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), player.registryAccess());
                            callReceive(handler, player, friendlyByteBuf, identifier);
                        });
                    }
                    success = true;
                    in.readerIndex(in.writerIndex());
                    return;
                }
            }
        } catch (Throwable t) {
            if (!(t instanceof IndexOutOfBoundsException && allowIndexOutOfBounds)) {
                // Skip remaining bytes
                success = true;
                in.skipBytes(in.readableBytes());

                // Throw error, will disconnect client
                throw t;
            }
        } finally {
            if (!success) {
                in.readerIndex(readerIndex);
            }
        }

        out.add(in.retain());
    }

    private static void callReceive(PacketHandler handler, ServerPlayer player, RegistryFriendlyByteBuf friendlyByteBuf, String identifier) {
        if (player.hasDisconnected()) {
            return;
        }
        try {
            handler.onReceive(player.getBukkitEntity(), friendlyByteBuf);
        } catch (Throwable t) {
            player.connection.disconnectAsync(net.minecraft.network.chat.Component.literal("Error while processing Axiom packet " + identifier + ": " + t.getMessage()), DisconnectionReason.UNKNOWN);
        }
    }

    public static void apply(ChannelPipeline pipeline, AxiomBigPayloadHandler handler) {
        if (pipeline.get("axiom-big-payload-handler") != null) {
            pipeline.remove("axiom-big-payload-handler");
        }
        pipeline.addBefore("decoder", "axiom-big-payload-handler", handler);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (this.handleUserEvents) {
            if (evt == ConnectionEvent.COMPRESSION_THRESHOLD_SET || evt == ConnectionEvent.COMPRESSION_DISABLED) {
                AxiomBigPayloadHandler handler = new AxiomBigPayloadHandler(this.payloadId, this.connection, this.packetHandlers, false);
                apply(ctx.pipeline(), handler);
                super.userEventTriggered(ctx, evt);
                handler.handleUserEvents = true;
                return;
            }
        }

        super.userEventTriggered(ctx, evt);
    }

}
