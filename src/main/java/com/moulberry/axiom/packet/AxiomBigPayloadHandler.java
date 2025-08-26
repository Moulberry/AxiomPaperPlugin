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
import io.papermc.paper.network.ConnectionEvent;
import net.kyori.adventure.text.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AxiomBigPayloadHandler extends MessageToMessageDecoder<ByteBuf> {

    private final int payloadId;
    private final Connection connection;
    private final Map<String, PacketHandler> packetHandlers;

    public AxiomBigPayloadHandler(int payloadId, Connection connection, Map<String, PacketHandler> packetHandlers) {
        this.payloadId = payloadId;
        this.connection = connection;
        this.packetHandlers = packetHandlers;
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
                    FriendlyByteBuf buf = new FriendlyByteBuf(in);
                    if (handler.handleAsync()) {
                        callReceive(handler, player, buf, identifier);
                    } else {
                        byte[] bytes = ByteBufUtil.getBytes(buf);

                        player.getServer().execute(() -> {
                            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
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

    private static void callReceive(PacketHandler handler, ServerPlayer player, FriendlyByteBuf friendlyByteBuf, String identifier) {
        if (player.hasDisconnected()) {
            return;
        }
        try {
            handler.onReceive(player.getBukkitEntity(), friendlyByteBuf);
        } catch (Throwable t) {
            player.server.execute(() -> {
                player.connection.disconnect(net.minecraft.network.chat.Component.literal("Error while processing Axiom packet " + identifier + ": " + t.getMessage()), PlayerKickEvent.Cause.UNKNOWN);
            });
            player.connection.connection.setReadOnly();
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
        if (evt == ConnectionEvent.COMPRESSION_THRESHOLD_SET || evt == ConnectionEvent.COMPRESSION_DISABLED) {
            apply(ctx.pipeline(), new AxiomBigPayloadHandler(this.payloadId, this.connection, this.packetHandlers));
        }
        super.userEventTriggered(ctx, evt);
    }

}
