package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.buffer.CompressedBlockEntity;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;

public class RequestBlockEntityPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;

    public RequestBlockEntityPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player bukkitPlayer, @NotNull byte[] message) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        long id = friendlyByteBuf.readLong();

        if (!bukkitPlayer.hasPermission("axiom.*")) {
            // We always send an 'empty' response in order to make the client happy
            sendEmptyResponse(bukkitPlayer, id);
            return;
        }

        ServerPlayer player = ((CraftPlayer)bukkitPlayer).getHandle();
        MinecraftServer server = player.getServer();
        if (server == null) {
            sendEmptyResponse(bukkitPlayer, id);
            return;
        }

        ResourceKey<Level> worldKey = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        ServerLevel level = server.getLevel(worldKey);
        if (level == null) {
            sendEmptyResponse(bukkitPlayer, id);
            return;
        }

        Long2ObjectMap<CompressedBlockEntity> map = new Long2ObjectOpenHashMap<>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        // Save and compress block entities
        int count = friendlyByteBuf.readVarInt();
        for (int i = 0; i < count; i++) {
            long pos = friendlyByteBuf.readLong();
            BlockEntity blockEntity = level.getBlockEntity(mutableBlockPos.set(pos));
            if (blockEntity != null) {
                CompoundTag tag = blockEntity.saveWithoutMetadata();
                map.put(pos, CompressedBlockEntity.compress(tag, baos));
            }
        }

        // Send response packet
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(16));
        buf.writeLong(id);
        buf.writeVarInt(map.size());
        for (Long2ObjectMap.Entry<CompressedBlockEntity> entry : map.long2ObjectEntrySet()) {
            buf.writeLong(entry.getLongKey());
            entry.getValue().write(buf);
        }
        bukkitPlayer.sendPluginMessage(this.plugin, "axiom:block_entities", buf.accessByteBufWithCorrectSize());
    }

    private void sendEmptyResponse(Player player, long id) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(16));
        buf.writeLong(id);
        buf.writeByte(0); // no block entities
        player.sendPluginMessage(this.plugin, "axiom:block_entities", buf.accessByteBufWithCorrectSize());
    }

}
