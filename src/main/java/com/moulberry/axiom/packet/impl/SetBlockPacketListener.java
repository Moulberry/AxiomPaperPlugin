package com.moulberry.axiom.packet.impl;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.AxiomReflection;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.coreprotect.CoreProtectIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.BlockHitResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_20_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R2.block.CraftBlockState;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R2.event.CraftEventFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

public class SetBlockPacketListener implements PacketHandler {

    private final AxiomPaper plugin;

    public SetBlockPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    public static class AxiomPlacingCraftBlockState extends CraftBlockState {
        public AxiomPlacingCraftBlockState(@Nullable World world, BlockPos blockPosition, BlockState blockData) {
            super(world, blockPosition, blockData);
        }
    }

    @Override
    public void onReceive(Player bukkitPlayer, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(bukkitPlayer, AxiomPermission.BUILD_PLACE)) {
            return;
        }

        if (!this.plugin.canModifyWorld(bukkitPlayer, bukkitPlayer.getWorld())) {
            return;
        }

        // Read packet
        IntFunction<Map<BlockPos, BlockState>> mapFunction = this.plugin.limitCollection(Maps::newLinkedHashMapWithExpectedSize);
        IdMapper<BlockState> registry = this.plugin.getBlockRegistry(bukkitPlayer.getUniqueId());
        Map<BlockPos, BlockState> blocks = friendlyByteBuf.readMap(mapFunction,
                FriendlyByteBuf::readBlockPos, buf -> buf.readById(registry));
        boolean updateNeighbors = friendlyByteBuf.readBoolean();
        Set<BlockPos> preventUpdatesAt = Set.of();
        if (updateNeighbors) {
            IntFunction<Set<BlockPos>> setFunction = this.plugin.limitCollection(Sets::newHashSetWithExpectedSize);
            preventUpdatesAt = friendlyByteBuf.readCollection(setFunction, buf -> buf.readBlockPos());
        }

        if (this.plugin.logLargeBlockBufferChanges() && blocks.size() > 64) {
            this.plugin.getLogger().info("Player " + bukkitPlayer.getUniqueId() + " modified " + blocks.size() + " individual blocks with axiom");
        }

        int reason = friendlyByteBuf.readVarInt();
        boolean breaking = friendlyByteBuf.readBoolean();
        BlockHitResult blockHit = friendlyByteBuf.readBlockHitResult();
        InteractionHand hand = friendlyByteBuf.readEnum(InteractionHand.class);
        int sequenceId = friendlyByteBuf.readVarInt();

        ServerPlayer player = ((CraftPlayer)bukkitPlayer).getHandle();
        CraftWorld world = player.level().getWorld();

        if (sequenceId >= 0) {
            player.connection.ackBlockChangesUpTo(sequenceId);
        }

        BlockPlaceContext blockPlaceContext = new BlockPlaceContext(player, hand, player.getItemInHand(hand), blockHit);

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
            return;
        }

        // Call BlockMultiPlace / BlockPlace event
        if (!breaking) {
            List<org.bukkit.block.BlockState> blockStates = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockState existing = player.serverLevel().getBlockState(entry.getKey());
                if (existing.canBeReplaced()) {
                    blockStates.add(new AxiomPlacingCraftBlockState(world, entry.getKey(), entry.getValue()));
                }
            }

            if (!blockStates.isEmpty()) {
                Cancellable event;
                if (blockStates.size() > 1) {
                    event = CraftEventFactory.callBlockMultiPlaceEvent(player.serverLevel(),
                        player, hand, blockStates, blockHit.getBlockPos().getX(),
                            blockHit.getBlockPos().getY(), blockHit.getBlockPos().getZ());
                } else {
                    event = CraftEventFactory.callBlockPlaceEvent(player.serverLevel(),
                        player, hand, blockStates.get(0), blockHit.getBlockPos().getX(),
                        blockHit.getBlockPos().getY(), blockHit.getBlockPos().getZ());
                }
                if (event.isCancelled()) {
                    return;
                }
            }
        }

        // Update blocks
        if (updateNeighbors) {
            if (preventUpdatesAt.isEmpty()) {
                for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                    BlockPos blockPos = entry.getKey();
                    BlockState blockState = entry.getValue();

                    if (!canBreakOrPlace(bukkitPlayer, blockState, world, blockPos)) {
                        continue;
                    }

                    boolean logPlacement = false;

                    if (CoreProtectIntegration.isEnabled()) {
                        BlockState old = player.level().getBlockState(blockPos);
                        if (old != blockState) {
                            CoreProtectIntegration.logRemoval(bukkitPlayer.getName(), old, world, blockPos);
                            logPlacement = true;
                        }
                    }

                    // Place block
                    player.level().setBlock(blockPos, blockState, 3);

                    if (logPlacement) {
                        CoreProtectIntegration.logPlacement(bukkitPlayer.getName(), blockState, world, blockPos);
                    }
                }
            } else {
                Direction[] directions = Direction.values();
                BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
                Map<BlockPos, BlockState> delayedSetWithoutUpdates = new LinkedHashMap<>(Math.min(blocks.size(), preventUpdatesAt.size()));
                for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                    BlockPos blockPos = entry.getKey();
                    BlockState blockState = entry.getValue();

                    if (!canBreakOrPlace(bukkitPlayer, blockState, world, blockPos)) {
                        continue;
                    }

                    // Check if we have a neighbor that shouldn't receive updates
                    // Unfortunately this will also prevent updates to ALL the other neighbors,
                    // but that case is rare enough for it not to matter
                    boolean updateNeighborsForThisBlock = true;
                    for (Direction direction : directions) {
                        if (preventUpdatesAt.contains(mutable.setWithOffset(blockPos, direction))) {
                            updateNeighborsForThisBlock = false;
                            break;
                        }
                    }

                    if (preventUpdatesAt.contains(blockPos)) {
                        delayedSetWithoutUpdates.put(blockPos, blockState);
                        if (!updateNeighborsForThisBlock) {
                            continue;
                        }
                    }

                    boolean logPlacement = false;

                    if (CoreProtectIntegration.isEnabled()) {
                        BlockState old = player.level().getBlockState(blockPos);
                        if (old != blockState) {
                            CoreProtectIntegration.logRemoval(bukkitPlayer.getName(), old, world, blockPos);
                            logPlacement = true;
                        }
                    }

                    player.level().setBlock(blockPos, blockState, updateNeighborsForThisBlock ? 3 : 18);

                    if (logPlacement) {
                        CoreProtectIntegration.logPlacement(bukkitPlayer.getName(), blockState, world, blockPos);
                    }
                }
                for (Map.Entry<BlockPos, BlockState> entry : delayedSetWithoutUpdates.entrySet()) {
                    setWithoutUpdates(bukkitPlayer, entry.getValue(), world, entry.getKey(), player);
                }
            }
        } else {
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos blockPos = entry.getKey();
                BlockState blockState = entry.getValue();

                setWithoutUpdates(bukkitPlayer, blockState, world, blockPos, player);
            }
        }

        if (!breaking) {
            BlockPos clickedPos = blockPlaceContext.getClickedPos();

            if (blocks.containsKey(clickedPos)) {
                // Disallow in unloaded chunks
                if (!player.level().isLoaded(clickedPos)) {
                    return;
                }

                BlockState desiredBlockState = blocks.get(clickedPos);
                BlockState actualBlockState = player.level().getBlockState(clickedPos);
                Block actualBlock = actualBlockState.getBlock();

                // Ensure block is correct
                if (desiredBlockState == null || desiredBlockState.isAir() || actualBlockState.isAir()) return;
                if (desiredBlockState.getBlock() != actualBlock) return;

                // Check plot squared
                if (!Integration.canPlaceBlock(bukkitPlayer, new Location(world, clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()))) {
                    return;
                }

                ItemStack inHand = player.getItemInHand(hand);

                BlockItem.updateCustomBlockEntityTag(player.level(), player, clickedPos, inHand);

                if (!(actualBlock instanceof BedBlock) && !(actualBlock instanceof DoublePlantBlock) && !(actualBlock instanceof DoorBlock)) {
                    actualBlock.setPlacedBy(player.level(), clickedPos, actualBlockState, player, inHand);
                }
            }
        }
    }

    private void setWithoutUpdates(Player bukkitPlayer, BlockState blockState, CraftWorld world, BlockPos blockPos, ServerPlayer player) {
        if (!canBreakOrPlace(bukkitPlayer, blockState, world, blockPos)) {
            return;
        }

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
        LevelChunk chunk = level.getChunkIfLoaded(cx, cz);
        if (chunk == null) return;
        chunk.setUnsaved(true);

        int sectionIndex = level.getSectionIndexFromSectionY(cy);
        if (sectionIndex < 0 || sectionIndex >= level.getSectionsCount()) return;

        LevelChunkSection section = chunk.getSection(sectionIndex);
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

        BlockState old = section.setBlockState(x, y, z, blockState, true);
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
                    AxiomReflection.updateBlockEntityTicker(chunk, blockEntity);
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

            // Mark block changed
            level.getChunkSource().blockChanged(blockPos);

            // Update Light
            if (LightEngine.hasDifferentLightProperties(chunk, blockPos, old, blockState)) {
                // Note: Skylight Sources not currently needed on Paper due to Starlight
                // This might change in the future, so be careful!
                // chunk.getSkyLightSources().update(chunk, x, by, z);
                level.getChunkSource().getLightEngine().checkBlock(blockPos);
            }

            // Update Poi
            Optional<Holder<PoiType>> newPoi = PoiTypes.forState(blockState);
            Optional<Holder<PoiType>> oldPoi = PoiTypes.forState(old);
            if (!Objects.equals(oldPoi, newPoi)) {
                if (oldPoi.isPresent()) level.getPoiManager().remove(blockPos);
                if (newPoi.isPresent()) level.getPoiManager().add(blockPos, newPoi.get());
            }

            if (CoreProtectIntegration.isEnabled()) {
                String changedBy = player.getBukkitEntity().getName();
                BlockPos changedPos = new BlockPos(bx, by, bz);

                CoreProtectIntegration.logRemoval(changedBy, old, world, changedPos);
                CoreProtectIntegration.logPlacement(changedBy, blockState, world, changedPos);
            }
        }

        boolean nowHasOnlyAir = section.hasOnlyAir();
        if (hasOnlyAir != nowHasOnlyAir) {
            level.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(cx, cy, cz), nowHasOnlyAir);
        }
    }

    private static boolean canBreakOrPlace(Player bukkitPlayer, BlockState blockState, CraftWorld world, BlockPos blockPos) {
        if (blockState == null) {
            return false;
        }
        if (!world.isChunkLoaded(blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
            return false;
        }
        if (blockState.isAir()) {
            return Integration.canBreakBlock(bukkitPlayer, world.getBlockAt(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
        } else {
            return Integration.canPlaceBlock(bukkitPlayer, new Location(world, blockPos.getX(), blockPos.getY(), blockPos.getZ()));
        }
    }

}
