package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.papermc.paper.network.ConnectionEvent;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;

public class AxiomBigPayloadHandler extends MessageToMessageDecoder<ByteBuf> {

    private static final ResourceLocation SET_BUFFER = VersionHelper.createResourceLocation("axiom", "set_buffer");
    private static final ResourceLocation UPLOAD_BLUEPRINT = VersionHelper.createResourceLocation("axiom", "upload_blueprint");
    private static final ResourceLocation UPDATE_ANNOTATIONS = VersionHelper.createResourceLocation("axiom", "annotation_update");
    private static final ResourceLocation REQUEST_CHUNK_DATA = VersionHelper.createResourceLocation("axiom", "request_chunk_data");
    private final int payloadId;
    private final Connection connection;
    private final SetBlockBufferPacketListener setBlockBuffer;
    private final UploadBlueprintPacketListener uploadBlueprint;
    private final UpdateAnnotationPacketListener updateAnnotation;
    private final RequestChunkDataPacketListener requestChunkDataPacketListener;

    public AxiomBigPayloadHandler(int payloadId, Connection connection, SetBlockBufferPacketListener setBlockBuffer,
            UploadBlueprintPacketListener uploadBlueprint, UpdateAnnotationPacketListener updateAnnotation,
            RequestChunkDataPacketListener requestChunkDataPacketListener) {
        this.payloadId = payloadId;
        this.connection = connection;
        this.setBlockBuffer = setBlockBuffer;
        this.uploadBlueprint = uploadBlueprint;
        this.updateAnnotation = updateAnnotation;
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
            out.add(in.retain());
            return;
        }

        // Don't process if channel isn't active
        if (!ctx.channel().isActive()) {
            in.skipBytes(in.readableBytes());
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
                } else if (identifier.equals(UPDATE_ANNOTATIONS)) {
                    updateAnnotation.onReceive(player, buf);
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
                    in.skipBytes(in.readableBytes());
                    return;
                }
            }
        } catch (Throwable t) {
            if (!(t instanceof IndexOutOfBoundsException)) {
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

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == ConnectionEvent.COMPRESSION_THRESHOLD_SET || evt == ConnectionEvent.COMPRESSION_DISABLED) {
            ctx.channel().pipeline().remove("axiom-big-payload-handler");
            ctx.channel().pipeline().addBefore("decoder", "axiom-big-payload-handler",
                                   new AxiomBigPayloadHandler(payloadId, connection, setBlockBuffer, uploadBlueprint, updateAnnotation, requestChunkDataPacketListener));
        }
        super.userEventTriggered(ctx, evt);
    }

}
