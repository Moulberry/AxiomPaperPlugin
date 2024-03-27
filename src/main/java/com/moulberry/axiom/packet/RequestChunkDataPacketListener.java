package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;

public class RequestChunkDataPacketListener implements PluginMessageListener {

    private static final ResourceLocation RESPONSE_ID = new ResourceLocation("axiom:response_chunk_data");

    private final AxiomPaper plugin;
    public RequestChunkDataPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player bukkitPlayer, @NotNull byte[] message) {
        ServerPlayer player = ((CraftPlayer)bukkitPlayer).getHandle();
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        long id = friendlyByteBuf.readLong();

        if (!this.plugin.canUseAxiom(bukkitPlayer)) {
            // We always send an 'empty' response in order to make the client happy
            sendEmptyResponse(player, id);
            return;
        }

        if (!this.plugin.canModifyWorld(bukkitPlayer, bukkitPlayer.getWorld())) {
            sendEmptyResponse(player, id);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            sendEmptyResponse(player, id);
            return;
        }

        ResourceKey<Level> worldKey = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        ServerLevel level = server.getLevel(worldKey);
        if (level == null) {
            sendEmptyResponse(player, id);
            return;
        }

        boolean sendBlockEntitiesInChunks = friendlyByteBuf.readBoolean();

        Long2ObjectMap<CompressedBlockEntity> blockEntityMap = new Long2ObjectOpenHashMap<>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        int maxChunkLoadDistance = this.plugin.configuration.getInt("max-chunk-load-distance");

        // Don't allow loading chunks outside render distance for plot worlds
        if (PlotSquaredIntegration.isPlotWorld(level.getWorld())) {
            maxChunkLoadDistance = 0;
        }

        int playerSectionX = player.getBlockX() >> 4;
        int playerSectionZ = player.getBlockZ() >> 4;

        // Save and compress block entities
        int count = friendlyByteBuf.readVarInt();
        for (int i = 0; i < count; i++) {
            long pos = friendlyByteBuf.readLong();
            mutableBlockPos.set(pos);

            if (level.isOutsideBuildHeight(mutableBlockPos)) continue;

            int chunkX = mutableBlockPos.getX() >> 4;
            int chunkZ = mutableBlockPos.getZ() >> 4;

            int distance = Math.abs(playerSectionX - chunkX) + Math.abs(playerSectionZ - chunkZ);
            boolean canLoad = distance <= maxChunkLoadDistance;

            LevelChunk chunk = (LevelChunk) level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, canLoad);
            if (chunk == null) continue;

            BlockEntity blockEntity = chunk.getBlockEntity(mutableBlockPos, LevelChunk.EntityCreationType.IMMEDIATE);
            if (blockEntity != null) {
                CompoundTag tag = blockEntity.saveWithoutMetadata();
                blockEntityMap.put(pos, CompressedBlockEntity.compress(tag, baos));
            }
        }

        Long2ObjectMap<PalettedContainer<BlockState>> sections = new Long2ObjectOpenHashMap<>();

        if (maxChunkLoadDistance > 0) {
            count = friendlyByteBuf.readVarInt();
            for (int i = 0; i < count; i++) {
                long pos = friendlyByteBuf.readLong();

                int sx = BlockPos.getX(pos);
                int sy = BlockPos.getY(pos);
                int sz = BlockPos.getZ(pos);

                int distance = Math.abs(playerSectionX - sx) + Math.abs(playerSectionZ - sz);
                if (distance > maxChunkLoadDistance) continue;

                LevelChunk chunk = level.getChunk(sx, sz);
                int sectionIndex = chunk.getSectionIndexFromSectionY(sy);
                if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) continue;
                LevelChunkSection section = chunk.getSection(sectionIndex);

                if (section.hasOnlyAir()) {
                    sections.put(pos, null);
                } else {
                    PalettedContainer<BlockState> container = section.getStates();
                    sections.put(pos, container);

                    if (sendBlockEntitiesInChunks && section.maybeHas(BlockState::hasBlockEntity)) {
                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    BlockState blockState = container.get(x, y, z);
                                    if (blockState.hasBlockEntity()) {
                                        mutableBlockPos.set(sx*16 + x, sy*16 + y, sz*16 + z);
                                        BlockEntity blockEntity = chunk.getBlockEntity(mutableBlockPos, LevelChunk.EntityCreationType.CHECK);
                                        if (blockEntity != null) {
                                            CompoundTag tag = blockEntity.saveWithoutMetadata();
                                            blockEntityMap.put(mutableBlockPos.asLong(), CompressedBlockEntity.compress(tag, baos));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Send response packet

        boolean firstPart = true;
        int maxSize = 0x100000 - 64; // Leeway of 64 bytes

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(id);

        for (Long2ObjectMap.Entry<CompressedBlockEntity> entry : blockEntityMap.long2ObjectEntrySet()) {
            int beforeWriterIndex = buf.writerIndex();

            buf.writeLong(entry.getLongKey());
            entry.getValue().write(buf);

            if (buf.writerIndex() >= maxSize) {
                if (firstPart) {
                    // Finish and send current packet
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
                    buf.writeBoolean(false);
                    byte[] bytes = new byte[buf.writerIndex()];
                    buf.getBytes(0, bytes);
                    VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);

                    // Continuation packet
                    buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeLong(id);
                } else {
                    // Copy extra bytes
                    int copiedSize = buf.writerIndex() - beforeWriterIndex;
                    byte[] copied = new byte[copiedSize];
                    buf.getBytes(beforeWriterIndex, copied);

                    // Discard extra bytes
                    buf.writerIndex(beforeWriterIndex);

                    // Finish and send current packet
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
                    buf.writeBoolean(false);
                    byte[] bytes = new byte[buf.writerIndex()];
                    buf.getBytes(0, bytes);
                    VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);

                    // Continuation packet
                    buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeLong(id);

                    // Write start of new packet
                    buf.writeBytes(copied);
                    firstPart = true;
                }
            } else {
                firstPart = false;
            }
        }

        buf.writeLong(AxiomConstants.MIN_POSITION_LONG);

        for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : sections.long2ObjectEntrySet()) {
            int beforeWriterIndex = buf.writerIndex();

            buf.writeLong(entry.getLongKey());
            var container = entry.getValue();
            if (container == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                entry.getValue().write(buf);
            }

            if (buf.writerIndex() >= maxSize) {
                if (firstPart) {
                    // Finish and send current packet
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
                    buf.writeBoolean(false);
                    byte[] bytes = new byte[buf.writerIndex()];
                    buf.getBytes(0, bytes);
                    VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);

                    // Continuation packet
                    buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeLong(id);
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
                } else {
                    // Copy extra bytes
                    int copiedSize = buf.writerIndex() - beforeWriterIndex;
                    byte[] copied = new byte[copiedSize];
                    buf.getBytes(beforeWriterIndex, copied);

                    // Discard extra bytes
                    buf.writerIndex(beforeWriterIndex);

                    // Finish and send current packet
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
                    buf.writeBoolean(false);
                    byte[] bytes = new byte[buf.writerIndex()];
                    buf.getBytes(0, bytes);
                    VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);

                    // Continuation packet
                    buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeLong(id);
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);

                    // Write start of new packet
                    buf.writeBytes(copied);
                    firstPart = true;
                }
            } else {
                firstPart = false;
            }
        }

        buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
        buf.writeBoolean(true);
        byte[] bytes = new byte[buf.writerIndex()];
        buf.getBytes(0, bytes);
        VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);
    }

    private void sendEmptyResponse(ServerPlayer player, long id) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(16));
        buf.writeLong(id);
        buf.writeLong(AxiomConstants.MIN_POSITION_LONG); // no block entities
        buf.writeLong(AxiomConstants.MIN_POSITION_LONG); // no chunks
        buf.writeBoolean(true); // finished

        byte[] bytes = new byte[buf.writerIndex()];
        buf.getBytes(0, bytes);
        VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);
    }

}
