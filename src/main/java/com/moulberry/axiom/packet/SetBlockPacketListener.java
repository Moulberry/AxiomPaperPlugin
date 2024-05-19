package com.moulberry.axiom.packet;

import com.google.common.collect.Maps;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.coreprotect.CoreProtectIntegration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_20_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;

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
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        try {
            this.process(player, message);
        } catch (Throwable t) {
            player.kick(Component.text("Error while processing packet " + channel + ": " + t.getMessage()));
        }
    }

    private void process(Player bukkitPlayer, byte[] message) {
        if (!this.plugin.canUseAxiom(bukkitPlayer)) {
            return;
        }

        if (!this.plugin.canModifyWorld(bukkitPlayer, bukkitPlayer.getWorld())) {
            return;
        }

        // Read packet
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        IntFunction<Map<BlockPos, BlockState>> mapFunction = FriendlyByteBuf.limitValue(Maps::newLinkedHashMapWithExpectedSize, 512);
        IdMapper<BlockState> registry = this.plugin.getBlockRegistry(bukkitPlayer.getUniqueId());
        Map<BlockPos, BlockState> blocks = friendlyByteBuf.readMap(mapFunction,
                FriendlyByteBuf::readBlockPos, buf -> buf.readById(registry));
        boolean updateNeighbors = friendlyByteBuf.readBoolean();

        int reason = friendlyByteBuf.readVarInt();
        boolean breaking = friendlyByteBuf.readBoolean();
        BlockHitResult blockHit = friendlyByteBuf.readBlockHitResult();
        InteractionHand hand = friendlyByteBuf.readEnum(InteractionHand.class);
        int sequenceId = friendlyByteBuf.readVarInt();

        ServerPlayer player = ((CraftPlayer)bukkitPlayer).getHandle();

        if (sequenceId >= 0) {
            player.connection.ackBlockChangesUpTo(sequenceId);
        }

        if (!blockHit.getLocation().equals(Vec3.ZERO)) {
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
        }

        CraftWorld world = player.level().getWorld();

        BlockPlaceContext blockPlaceContext = new BlockPlaceContext(player, hand, player.getItemInHand(hand), blockHit);

        // Update blocks
        if (updateNeighbors) {
            int count = 0;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                if (count++ > 64) break;

                BlockPos blockPos = entry.getKey();
                BlockState blockState = entry.getValue();

                if (blockState == null) {
                    continue;
                }

                // Disallow in unloaded chunks
                if (!player.level().isLoaded(blockPos)) {
                    continue;
                }

                // Check PlotSquared
                if (blockState.isAir()) {
                    if (!Integration.canBreakBlock(bukkitPlayer, world.getBlockAt(blockPos.getX(), blockPos.getY(), blockPos.getZ()))) {
                        continue;
                    }
                } else if (!Integration.canPlaceBlock(bukkitPlayer, new Location(world, blockPos.getX(), blockPos.getY(), blockPos.getZ()))) {
                    continue;
                }

                CoreProtectIntegration.logRemoval(bukkitPlayer.getName(), player.level(), world, blockPos);

                // Place block
                player.level().setBlock(blockPos, blockState, 3);
                CoreProtectIntegration.logPlacement(bukkitPlayer.getName(), blockState, world, blockPos);
            }
        } else {
            int count = 0;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                if (count++ > 64) break;

                BlockPos blockPos = entry.getKey();
                BlockState blockState = entry.getValue();

                if (blockState == null) {
                    continue;
                }

                // Disallow in unloaded chunks
                if (!player.level().isLoaded(blockPos)) {
                    continue;
                }

                // Check PlotSquared
                if (blockState.isAir()) {
                    if (!Integration.canBreakBlock(bukkitPlayer, world.getBlockAt(blockPos.getX(), blockPos.getY(), blockPos.getZ()))) {
                        continue;
                    }
                } else if (!Integration.canPlaceBlock(bukkitPlayer, new Location(world, blockPos.getX(), blockPos.getY(), blockPos.getZ()))) {
                    continue;
                }

                // Place block
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

                BlockState old = section.setBlockState(x, y, z, blockState, true);
                if (blockState != old) {
                    CoreProtectIntegration.logRemoval(bukkitPlayer.getName(), old, world, blockPos);
                    CoreProtectIntegration.logPlacement(bukkitPlayer.getName(), blockState, world, blockPos);

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
                }

                boolean nowHasOnlyAir = section.hasOnlyAir();
                if (hasOnlyAir != nowHasOnlyAir) {
                    level.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(cx, cy, cz), nowHasOnlyAir);
                }
            }
        }

        if (!breaking) {
            BlockPos clickedPos = blockPlaceContext.getClickedPos();

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
