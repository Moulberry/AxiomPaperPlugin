package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.papermc.paper.network.ConnectionEvent;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;

public class AxiomBigPayloadHandler extends ByteToMessageDecoder {

    private static final ResourceLocation SET_BUFFER = new ResourceLocation("axiom", "set_buffer");
    private static final ResourceLocation UPLOAD_BLUEPRINT = new ResourceLocation("axiom", "upload_blueprint");
    private static final ResourceLocation REQUEST_CHUNK_DATA = new ResourceLocation("axiom", "request_chunk_data");
    private final int payloadId;
    private final Connection connection;
    private final SetBlockBufferPacketListener setBlockBuffer;
    private final UploadBlueprintPacketListener uploadBlueprint;
    private final RequestChunkDataPacketListener requestChunkDataPacketListener;

    public AxiomBigPayloadHandler(int payloadId, Connection connection, SetBlockBufferPacketListener setBlockBuffer,
            UploadBlueprintPacketListener uploadBlueprint, RequestChunkDataPacketListener requestChunkDataPacketListener) {
        this.payloadId = payloadId;
        this.connection = connection;
        this.setBlockBuffer = setBlockBuffer;
        this.uploadBlueprint = uploadBlueprint;
        this.requestChunkDataPacketListener = requestChunkDataPacketListener;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // No bytes to read?! Go away
        if (in.readableBytes() == 0) {
            return;
        }

        // Don't handle if player doesn't have permission to use Axiom
        ServerPlayer player = connection.getPlayer();
        if (player == null || !AxiomPaper.PLUGIN.canUseAxiom(player.getBukkitEntity())) {
            ctx.fireChannelRead(in.retain());

            // Skip remaining bytes
            if (in.readableBytes() > 0) {
                in.skipBytes(in.readableBytes());
            }
            return;
        }

        // Don't process if channel isn't active
        if (!ctx.channel().isActive()) {
            if (in.readableBytes() > 0) {
                in.skipBytes(in.readableBytes());
            }
            return;
        }

        int readerIndex = in.readerIndex();
        boolean success = false;
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(in);
            int packetId = buf.readVarInt();

            if (packetId == payloadId) {
                ResourceLocation identifier = buf.readResourceLocation();
                if (identifier.equals(SET_BUFFER)) {
                    setBlockBuffer.onReceive(player, buf);
                    success = true;
                    if (in.readableBytes() > 0) {
                        throw new IOException("Axiom packet " + identifier + " was larger than I expected, found " + in.readableBytes() +
                            " bytes extra whilst reading packet");
                    }
                    return;
                } else if (identifier.equals(UPLOAD_BLUEPRINT)) {
                    uploadBlueprint.onReceive(player, buf);
                    success = true;
                    if (in.readableBytes() > 0) {
                        throw new IOException("Axiom packet " + identifier + " was larger than I expected, found " + in.readableBytes() +
                            " bytes extra whilst reading packet");
                    }
                    return;
                } else if (requestChunkDataPacketListener != null && identifier.equals(REQUEST_CHUNK_DATA)) {
                    byte[] bytes = new byte[buf.writerIndex() - buf.readerIndex()];
                    buf.getBytes(buf.readerIndex(), bytes);

                    player.getServer().execute(() -> {
                        try {
                            requestChunkDataPacketListener.onPluginMessageReceived(
                                identifier.toString(), player.getBukkitEntity(), bytes);
                        } catch (Throwable t) {
                            player.getBukkitEntity().kick(net.kyori.adventure.text.Component.text(
                                "An error occured while requesting chunk data: " + t.getMessage()));
                        }
                    });

                    success = true;
                    if (in.readableBytes() > 0) {
                        in.skipBytes(in.readableBytes());
                    }
                    return;
                }
            }
        } catch (Throwable t) {
            if (!(t instanceof IndexOutOfBoundsException)) {
                // Skip remaining bytes
                success = true;
                if (in.readableBytes() > 0) {
                    in.skipBytes(in.readableBytes());
                }

                // Throw error, will disconnect client
                throw t;
            }
        } finally {
            if (!success) {
                in.readerIndex(readerIndex);
            }
        }

        ctx.fireChannelRead(in.retain());

        // Skip remaining bytes
        if (in.readableBytes() > 0) {
            in.skipBytes(in.readableBytes());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == ConnectionEvent.COMPRESSION_THRESHOLD_SET || evt == ConnectionEvent.COMPRESSION_DISABLED) {
            ctx.channel().pipeline().remove("axiom-big-payload-handler");
            ctx.channel().pipeline().addBefore("decoder", "axiom-big-payload-handler",
                                               new AxiomBigPayloadHandler(payloadId, connection, setBlockBuffer, uploadBlueprint, requestChunkDataPacketListener));
        }
        super.userEventTriggered(ctx, evt);
    }

}
