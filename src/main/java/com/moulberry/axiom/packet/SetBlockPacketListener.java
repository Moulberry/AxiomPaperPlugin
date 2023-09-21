package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomModifyWorldEvent;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.BlockHitResult;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;

public class SetBlockPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;
    private final Method updateBlockEntityTicker;

    public SetBlockPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;

        ReflectionRemapper reflectionRemapper = ReflectionRemapper.forReobfMappingsInPaperJar();
        String methodName = reflectionRemapper.remapMethodName(LevelChunk.class, "updateBlockEntityTicker", BlockEntity.class);

        try {
            this.updateBlockEntityTicker = LevelChunk.class.getDeclaredMethod(methodName, BlockEntity.class);
            this.updateBlockEntityTicker.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player bukkitPlayer, @NotNull byte[] message) {
        if (!bukkitPlayer.hasPermission("axiom.*")) {
            return;
        }

        // Check if player is allowed to modify this world
        AxiomModifyWorldEvent modifyWorldEvent = new AxiomModifyWorldEvent(bukkitPlayer, bukkitPlayer.getWorld());
        Bukkit.getPluginManager().callEvent(modifyWorldEvent);
        if (modifyWorldEvent.isCancelled()) return;

        // Read packet
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        Map<BlockPos, BlockState> blocks = friendlyByteBuf.readMap(FriendlyByteBuf::readBlockPos, buf -> buf.readById(Block.BLOCK_STATE_REGISTRY));
        boolean updateNeighbors = friendlyByteBuf.readBoolean();

        int reason = friendlyByteBuf.readVarInt();
        boolean breaking = friendlyByteBuf.readBoolean();
        BlockHitResult blockHit = friendlyByteBuf.readBlockHitResult();
        InteractionHand hand = friendlyByteBuf.readEnum(InteractionHand.class);
        int sequenceId = friendlyByteBuf.readVarInt();

        ServerPlayer player = ((CraftPlayer)bukkitPlayer).getHandle();

        org.bukkit.inventory.ItemStack heldItem;
        if (hand == InteractionHand.MAIN_HAND) {
            heldItem = bukkitPlayer.getInventory().getItemInMainHand();
        } else {
            heldItem = bukkitPlayer.getInventory().getItemInOffHand();
        }

        org.bukkit.block.Block blockClicked = bukkitPlayer.getWorld().getBlockAt(blockHit.getBlockPos().getX(),
            blockHit.getBlockPos().getY(), blockHit.getBlockPos().getZ());

        BlockFace blockFace = CraftBlock.notchToBlockFace(blockHit.getDirection());

        // Call interact event
        PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(bukkitPlayer,
            breaking ? Action.LEFT_CLICK_BLOCK : Action.RIGHT_CLICK_BLOCK, heldItem, blockClicked, blockFace);
        if (!playerInteractEvent.callEvent()) {
            if (sequenceId >= 0) {
                player.connection.ackBlockChangesUpTo(sequenceId);
            }
            return;
        }

        // Update blocks
        if (updateNeighbors) {
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                player.level().setBlock(entry.getKey(), entry.getValue(), 3);
            }
        } else {
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos blockPos = entry.getKey();
                BlockState blockState = entry.getValue();

                int bx = blockPos.getX();
                int by = blockPos.getY();
                int bz = blockPos.getZ();
                int x = bx & 0xF;
                int y = by & 0xF;
                int z = bz & 0xF;
                int cx = bx >> 4;
                int cy = by >> 4;
                int cz = bz >> 4;

                ServerLevel level = player.serverLevel();
                LevelChunk chunk = level.getChunk(cx, cz);
                chunk.setUnsaved(true);

                LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(cy));
                boolean hasOnlyAir = section.hasOnlyAir();

                Heightmap worldSurface = null;
                Heightmap oceanFloor = null;
                Heightmap motionBlocking = null;
                Heightmap motionBlockingNoLeaves = null;
                for (Map.Entry<Heightmap.Types, Heightmap> heightmap : chunk.getHeightmaps()) {
                    switch (heightmap.getKey()) {
                        case WORLD_SURFACE -> worldSurface = heightmap.getValue();
                        case OCEAN_FLOOR -> oceanFloor = heightmap.getValue();
                        case MOTION_BLOCKING -> motionBlocking = heightmap.getValue();
                        case MOTION_BLOCKING_NO_LEAVES -> motionBlockingNoLeaves = heightmap.getValue();
                        default -> {}
                    }
                }

                BlockState old = section.setBlockState(x, y, z, blockState, false);
                if (blockState != old) {
                    Block block = blockState.getBlock();
                    motionBlocking.update(x, by, z, blockState);
                    motionBlockingNoLeaves.update(x, by, z, blockState);
                    oceanFloor.update(x, by, z, blockState);
                    worldSurface.update(x, by, z, blockState);

                    if (blockState.hasBlockEntity()) {
                        BlockEntity blockEntity = chunk.getBlockEntity(blockPos, LevelChunk.EntityCreationType.CHECK);

                        if (blockEntity == null) {
                            // There isn't a block entity here, create it!
                            blockEntity = ((EntityBlock)block).newBlockEntity(blockPos, blockState);
                            if (blockEntity != null) {
                                chunk.addAndRegisterBlockEntity(blockEntity);
                            }
                        } else if (blockEntity.getType().isValid(blockState)) {
                            // Block entity is here and the type is correct
                            // Just update the state and ticker and move on
                            blockEntity.setBlockState(blockState);

                            try {
                                this.updateBlockEntityTicker.invoke(chunk, blockEntity);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            // Block entity type isn't correct, we need to recreate it
                            chunk.removeBlockEntity(blockPos);

                            blockEntity = ((EntityBlock)block).newBlockEntity(blockPos, blockState);
                            if (blockEntity != null) {
                                chunk.addAndRegisterBlockEntity(blockEntity);
                            }
                        }
                    } else if (old.hasBlockEntity()) {
                        chunk.removeBlockEntity(blockPos);
                    }

                    level.getChunkSource().blockChanged(blockPos);
                    if (LightEngine.hasDifferentLightProperties(chunk, blockPos, old, blockState)) {
                        level.getChunkSource().getLightEngine().checkBlock(blockPos);
                    }
                }

                boolean nowHasOnlyAir = section.hasOnlyAir();
                if (hasOnlyAir != nowHasOnlyAir) {
                    level.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(cx, cy, cz), nowHasOnlyAir);
                }
            }
        }

        if (sequenceId >= 0) {
            player.connection.ackBlockChangesUpTo(sequenceId);
        }
    }

}
