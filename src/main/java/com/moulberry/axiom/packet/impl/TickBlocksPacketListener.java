package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.buffer.PositionSet;
import com.moulberry.axiom.buffer.TriIntConsumer;
import com.moulberry.axiom.integration.changelog.ChangeLogIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class TickBlocksPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public TickBlocksPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player bukkitPlayer, FriendlyByteBuf friendlyByteBuf) {
        var player = ((CraftPlayer)bukkitPlayer).getHandle();

        var level = player.level();
        if (level == null) {
            return;
        }

        var server = level.getServer();

        if (!this.plugin.canUseAxiom(bukkitPlayer, AxiomPermission.BUILD_DANGEROUS_TICK)) {
            return;
        }

        var world = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        PositionSet positionSet;
        BlockPos aabbMin;
        BlockPos aabbMax;

        byte type = friendlyByteBuf.readByte();
        if (type == 0) {
            positionSet = PositionSet.read(friendlyByteBuf);
            aabbMin = null;
            aabbMax = null;
        } else if (type == 1) {
            positionSet = null;
            aabbMin = friendlyByteBuf.readBlockPos();
            aabbMax = friendlyByteBuf.readBlockPos();
        } else {
            throw new RuntimeException("Unknown type: " + type);
        }

        if (level.dimension() != world) {
            return;
        }

        int count;
        if (positionSet != null) {
            count = positionSet.count();
        } else {
            int sizeX = Math.abs(aabbMax.getX() - aabbMin.getX()) + 1;
            int sizeY = Math.abs(aabbMax.getY() - aabbMin.getY()) + 1;
            int sizeZ = Math.abs(aabbMax.getZ() - aabbMin.getZ()) + 1;
            count = sizeX * sizeY * sizeZ;
        }
        boolean showMessage = count > 1048576;

        if (showMessage) {
            Component msg = Component.literal(player.getScoreboardName() + " updated & ticked " + count + " blocks using Axiom. The server may lag...");
            server.getPlayerList().broadcastSystemMessage(msg, false);

            long estimatedTime = Math.max(1, count / 2097152);
            msg = Component.literal("Estimated Time (varies depending on server hardware): " + estimatedTime + "s");
            server.getPlayerList().broadcastSystemMessage(msg, false);

            if (estimatedTime > 30) {
                msg = Component.literal("Estimated time is >30s, expect to be kicked from the server");
                server.getPlayerList().broadcastSystemMessage(msg, false);
            }
        }

        long start = System.currentTimeMillis();

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        TriIntConsumer consumer = (x, y, z) -> {
            blockPos.set(x, y, z);

            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.isAir()) {
                return;
            }

            String oldBlockEntityNbt = ChangeLogIntegration.requiresBlockEntitySnapshots()
                ? serializeBlockEntityNbt(level, blockPos)
                : null;

            FluidState fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                fluidState.tick(level, blockPos, blockState);
            }

            if (blockState.getBlock() instanceof LiquidBlock) {
                blockState.tick(level, blockPos, level.getRandom());
            } else {
                BlockState blockStateNew = Block.updateFromNeighbourShapes(blockState, level, blockPos);
                if (blockStateNew != blockState) {
                    level.setBlock(blockPos, blockStateNew, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
                }
            }

            if (ChangeLogIntegration.isEnabled()) {
                BlockState newBlockState = level.getBlockState(blockPos);
                String newBlockEntityNbt = ChangeLogIntegration.requiresBlockEntitySnapshots()
                    ? serializeBlockEntityNbt(level, blockPos)
                    : null;
                ChangeLogIntegration.logChange(
                    bukkitPlayer,
                    blockState,
                    oldBlockEntityNbt,
                    newBlockState,
                    newBlockEntityNbt,
                    (CraftWorld) bukkitPlayer.getWorld(),
                    blockPos.immutable()
                );
            }
        };
        if (positionSet != null) {
            positionSet.forEach(consumer);
        } else {
            int minX = Math.min(aabbMin.getX(), aabbMax.getX());
            int minY = Math.min(aabbMin.getY(), aabbMax.getY());
            int minZ = Math.min(aabbMin.getZ(), aabbMax.getZ());
            int maxX = Math.max(aabbMin.getX(), aabbMax.getX());
            int maxY = Math.max(aabbMin.getY(), aabbMax.getY());
            int maxZ = Math.max(aabbMin.getZ(), aabbMax.getZ());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        consumer.accept(x, y, z);
                    }
                }
            }
        }

        long end = System.currentTimeMillis();

        if (showMessage) {
            long seconds = (end - start + 500)/1000;
            Component msg = Component.literal("Done updating & ticking blocks (took " + seconds + "s)");
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }

    @Nullable
    private static String serializeBlockEntityNbt(net.minecraft.server.level.ServerLevel level, BlockPos blockPos) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity == null) {
            return null;
        }

        CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
        return tag.toString();
    }

}
