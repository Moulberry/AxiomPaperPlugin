package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomConstants;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.operations.RequestChunksOperation;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RequestChunkDataPacketListener implements PacketHandler {

    private static final ResourceLocation RESPONSE_ID = VersionHelper.createResourceLocation("axiom:response_chunk_data");

    private final AxiomPaper plugin;
    public RequestChunkDataPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player bukkitPlayer, FriendlyByteBuf friendlyByteBuf) {
        ServerPlayer player = ((CraftPlayer)bukkitPlayer).getHandle();
        long id = friendlyByteBuf.readLong();

        if (!this.plugin.canUseAxiom(bukkitPlayer, AxiomPermission.CHUNK_REQUEST) || this.plugin.isMismatchedDataVersion(bukkitPlayer.getUniqueId())) {
            // We always send an 'empty' response in order to make the client happy
            sendEmptyResponse(player, id);
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }

        if (!this.plugin.canModifyWorld(bukkitPlayer, bukkitPlayer.getWorld())) {
            sendEmptyResponse(player, id);
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            sendEmptyResponse(player, id);
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }

        ResourceKey<Level> worldKey = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        ServerLevel level = server.getLevel(worldKey);
        if (level == null || level != player.serverLevel()) {
            sendEmptyResponse(player, id);
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }

        boolean shouldSendBlockEntities = this.plugin.hasPermission(bukkitPlayer, AxiomPermission.CHUNK_REQUESTBLOCKENTITY);
        boolean sendBlockEntitiesInChunks = friendlyByteBuf.readBoolean() && shouldSendBlockEntities;

        int maxChunkLoadDistance = this.plugin.getMaxChunkLoadDistance(level.getWorld());

        if (!shouldSendBlockEntities && maxChunkLoadDistance <= 0) {
            sendEmptyResponse(player, id);
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }

        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        int playerSectionX = player.getBlockX() >> 4;
        int playerSectionZ = player.getBlockZ() >> 4;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Long2ObjectOpenHashMap<PalettedContainer<BlockState>> sendingSections = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<CompressedBlockEntity> sendingBlockEntities = new Long2ObjectOpenHashMap<>();

        LongSet chunkFutures = new LongOpenHashSet();
        Long2ObjectMap<LongList> sendBlockEntityForPendingChunks = new Long2ObjectOpenHashMap<>();
        Long2ObjectMap<IntList> sendSectionsForPendingChunks = new Long2ObjectOpenHashMap<>();

        int blockEntityCount = friendlyByteBuf.readVarInt();
        if (!shouldSendBlockEntities) {
            friendlyByteBuf.skipBytes(Long.BYTES * blockEntityCount);
        } else {
            for (int i = 0; i < blockEntityCount; i++) {

                long pos = friendlyByteBuf.readLong();
                mutableBlockPos.set(pos);

                if (level.isOutsideBuildHeight(mutableBlockPos)) {
                    continue;
                }

                int chunkX = mutableBlockPos.getX() >> 4;
                int chunkZ = mutableBlockPos.getZ() >> 4;

                int distance = Math.abs(playerSectionX - chunkX) + Math.abs(playerSectionZ - chunkZ);
                boolean canLoad = distance < maxChunkLoadDistance;

                if (!canLoad) {
                    LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
                    if (chunk == null) continue;

                    BlockEntity blockEntity = chunk.getBlockEntity(mutableBlockPos, LevelChunk.EntityCreationType.CHECK);
                    if (blockEntity != null) {
                        CompoundTag tag = blockEntity.saveWithoutMetadata();
                        sendingBlockEntities.put(pos, CompressedBlockEntity.compress(tag, baos));
                    }
                } else {
                    long chunkPosLong = ChunkPos.asLong(chunkX, chunkZ);
                    LongList blockEntitiesInChunk = sendBlockEntityForPendingChunks.get(chunkPosLong);
                    if (blockEntitiesInChunk != null) {
                        blockEntitiesInChunk.add(pos);
                    } else {
                        chunkFutures.add(ChunkPos.asLong(chunkX, chunkZ));

                        blockEntitiesInChunk = new LongArrayList();
                        blockEntitiesInChunk.add(pos);
                        sendBlockEntityForPendingChunks.put(chunkPosLong, blockEntitiesInChunk);
                    }
                }
            }
        }

        int chunkCount = friendlyByteBuf.readVarInt();
        if (maxChunkLoadDistance <= 0) {
            friendlyByteBuf.skipBytes(Long.BYTES * chunkCount);
        } else {
            for (int i = 0; i < chunkCount; i++) {
                long pos = friendlyByteBuf.readLong();

                int sx = BlockPos.getX(pos);
                int sy = BlockPos.getY(pos);
                int sz = BlockPos.getZ(pos);

                int distance = Math.abs(playerSectionX - sx) + Math.abs(playerSectionZ - sz);
                boolean canLoad = distance < maxChunkLoadDistance;

                if (!canLoad) {
                    LevelChunk chunk = level.getChunkIfLoaded(sx, sz);
                    if (chunk == null) continue;

                    int sectionIndex = chunk.getSectionIndexFromSectionY(sy);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) continue;
                    LevelChunkSection section = chunk.getSection(sectionIndex);

                    if (section.hasOnlyAir()) {
                        sendingSections.put(pos, null);
                    } else {
                        PalettedContainer<BlockState> container = section.getStates();
                        sendingSections.put(pos, container);

                        if (sendBlockEntitiesInChunks) {
                            Set<Map.Entry<BlockPos, BlockEntity>> entrySet = chunk.blockEntities.entrySet();
                            Iterator<Map.Entry<BlockPos, BlockEntity>> iterator;
                            if (entrySet instanceof Object2ObjectMap.FastEntrySet fastEntrySet) {
                                iterator = fastEntrySet.fastIterator();
                            } else {
                                iterator = entrySet.iterator();
                            }

                            while (iterator.hasNext()) {
                                Map.Entry<BlockPos, BlockEntity> entry = iterator.next();

                                BlockPos blockPos = entry.getKey();
                                int sectionY = blockPos.getY() >> 4;
                                if (sectionY != sy) {
                                    continue;
                                }

                                CompoundTag tag = entry.getValue().saveWithoutMetadata();
                                sendingBlockEntities.put(blockPos.asLong(), CompressedBlockEntity.compress(tag, baos));
                            }
                        }
                    }
                } else {
                    long chunkPosLong = ChunkPos.asLong(sx, sz);
                    IntList sendSections = sendSectionsForPendingChunks.get(chunkPosLong);
                    if (sendSections != null) {
                        sendSections.add(sy);
                    } else {
                        chunkFutures.add(ChunkPos.asLong(sx, sz));

                        sendSections = new IntArrayList();
                        sendSections.add(sy);
                        sendSectionsForPendingChunks.put(chunkPosLong, sendSections);
                    }
                }
            }
        }

        if (chunkFutures.isEmpty()) {
            sendResponse(player, id, sendingBlockEntities, sendingSections);
        } else {
            this.plugin.addPendingOperation(level, new RequestChunksOperation(player, id, chunkFutures, sendBlockEntityForPendingChunks, sendSectionsForPendingChunks,
                sendBlockEntitiesInChunks, sendingSections, sendingBlockEntities, baos));
        }
    }

    public static void sendResponse(ServerPlayer player, long id, Long2ObjectOpenHashMap<CompressedBlockEntity> sendingBlockEntities,
        Long2ObjectOpenHashMap<PalettedContainer<BlockState>> sendingSections) {
        boolean firstPart = true;
        int maxSize = 0x100000 - 64; // Leeway of 64 bytes

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(id);

        var blockEntityIterator = sendingBlockEntities.long2ObjectEntrySet().fastIterator();
        while (blockEntityIterator.hasNext()) {
            Long2ObjectMap.Entry<CompressedBlockEntity> entry = blockEntityIterator.next();
            int beforeWriterIndex = buf.writerIndex();

            buf.writeLong(entry.getLongKey());
            entry.getValue().write(buf);

            if (buf.writerIndex() >= maxSize) {
                if (firstPart) {
                    // Finish and send current packet
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
                    buf.writeLong(AxiomConstants.MIN_POSITION_LONG);
                    buf.writeBoolean(false);
                    byte[] bytes = ByteBufUtil.getBytes(buf);
                    VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);

                    // Continuation packet
                    buf.clear();
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
                    byte[] bytes = ByteBufUtil.getBytes(buf);
                    VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);

                    // Continuation packet
                    buf.clear();
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

        var sectionIterator = sendingSections.long2ObjectEntrySet().fastIterator();
        while (sectionIterator.hasNext()) {
            Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry = sectionIterator.next();
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
                    byte[] bytes = ByteBufUtil.getBytes(buf);
                    VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);

                    // Continuation packet
                    buf.clear();
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
                    byte[] bytes = ByteBufUtil.getBytes(buf);
                    VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);

                    // Continuation packet
                    buf.clear();
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
        byte[] bytes = ByteBufUtil.getBytes(buf);
        VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);
    }

    private void sendEmptyResponse(ServerPlayer player, long id) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(16));
        buf.writeLong(id);
        buf.writeLong(AxiomConstants.MIN_POSITION_LONG); // no block entities
        buf.writeLong(AxiomConstants.MIN_POSITION_LONG); // no chunks
        buf.writeBoolean(true); // finished

        byte[] bytes = ByteBufUtil.getBytes(buf);
        VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);
    }

}
