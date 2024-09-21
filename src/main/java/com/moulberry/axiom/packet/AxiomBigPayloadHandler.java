package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.packet.impl.RequestChunkDataPacketListener;
import com.moulberry.axiom.packet.impl.SetBlockBufferPacketListener;
import com.moulberry.axiom.packet.impl.UpdateAnnotationPacketListener;
import com.moulberry.axiom.packet.impl.UploadBlueprintPacketListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.papermc.paper.network.ConnectionEvent;
import net.kyori.adventure.text.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;

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
                String identifier = buf.readUtf(32767);
                PacketHandler handler = this.packetHandlers.get(identifier);
                if (handler != null) {
                    if (handler.handleAsync()) {
                        handler.onReceive(player.getBukkitEntity(), buf);
                        success = true;
                    } else {
                        byte[] bytes = new byte[buf.writerIndex() - buf.readerIndex()];
                        buf.getBytes(buf.readerIndex(), bytes);
                        Player bukkitPlayer = player.getBukkitEntity();

                        player.getServer().execute(() -> {
                            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                            try {
                                handler.onReceive(bukkitPlayer, friendlyByteBuf);
                            } catch (Throwable t) {
                                bukkitPlayer.kick(Component.text("Error while processing packet " + identifier + ": " + t.getMessage()));
                            }
                        });

                        success = true;
                        in.readerIndex(in.writerIndex());
                    }
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
                                   new AxiomBigPayloadHandler(payloadId, connection, packetHandlers));
        }
        super.userEventTriggered(ctx, evt);
    }

}
