package com.moulberry.axiom.packet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.papermc.paper.network.ConnectionEvent;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class AxiomBigPayloadHandler extends ByteToMessageDecoder {

    private static final ResourceLocation SET_BUFFER = new ResourceLocation("axiom", "set_buffer");
    private final int payloadId;
    private final Connection connection;
    private final SetBlockBufferPacketListener listener;

    public AxiomBigPayloadHandler(int payloadId, Connection connection, SetBlockBufferPacketListener listener) {
        this.payloadId = payloadId;
        this.connection = connection;
        this.listener = listener;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            int readerIndex = in.readerIndex();
            int i = in.readableBytes();
            if (i != 0) {
                FriendlyByteBuf buf = new FriendlyByteBuf(in);
                int packetId = buf.readVarInt();

                if (packetId == payloadId) {
                    ResourceLocation identifier = buf.readResourceLocation();
                    if (identifier.equals(SET_BUFFER)) {
                        ServerPlayer player = connection.getPlayer();
                        if (player != null && player.getBukkitEntity().hasPermission("axiom.*")) {
                            if (listener.onReceive(player, buf)) {
                                return;
                            }
                        }
                    }
                }
            }
            in.readerIndex(readerIndex);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ctx.fireChannelRead(in.retain());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == ConnectionEvent.COMPRESSION_THRESHOLD_SET || evt == ConnectionEvent.COMPRESSION_DISABLED) {
            ctx.channel().pipeline().remove("axiom-big-payload-handler");
            ctx.channel().pipeline().addBefore("decoder", "axiom-big-payload-handler",
                                               new AxiomBigPayloadHandler(payloadId, connection, listener));
        }
        super.userEventTriggered(ctx, evt);
    }

}
