package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.NbtSanitization;
import com.moulberry.axiom.event.AxiomSpawnEntityEvent;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import com.moulberry.axiom.viaversion.UnknownVersionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpawnEntityPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SpawnEntityPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    private record SpawnEntry(UUID newUuid, double x, double y, double z, float yaw, float pitch,
                              @Nullable UUID copyFrom, CompoundTag tag) {
    }

    private static final Rotation[] ROTATION_VALUES = Rotation.values();

    @Override
    public void onReceive(Player player, RegistryFriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.ENTITY_SPAWN)) {
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        List<SpawnEntry> entries = friendlyByteBuf.readCollection(this.plugin.limitCollection(ArrayList::new),
            buf -> new SpawnEntry(buf.readUUID(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readFloat(),
                buf.readNullable(buffer -> buffer.readUUID()), UnknownVersionHelper.readTagUnknown(buf, player)));

        ServerLevel serverLevel = ((CraftWorld)player.getWorld()).getHandle();

        for (SpawnEntry entry : entries) {
            Vec3 position = new Vec3(entry.x, entry.y, entry.z);

            BlockPos blockPos = BlockPos.containing(position);
            if (!Level.isInSpawnableBounds(blockPos)) {
                continue;
            }

            if (!Integration.canPlaceBlock(player, new Location(player.getWorld(),
                    blockPos.getX(), blockPos.getY(), blockPos.getZ()))) {
                continue;
            }

            if (serverLevel.getEntity(entry.newUuid) != null) continue;

            CompoundTag tag = entry.tag == null ? new CompoundTag() : entry.tag;

            NbtSanitization.sanitizeEntity(tag);

            if (entry.copyFrom != null) {
                Entity entityCopyFrom = serverLevel.getEntity(entry.copyFrom);
                if (entityCopyFrom != null) {
                    var valueOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entityCopyFrom.registryAccess());
                    CompoundTag saved = entityCopyFrom.saveAsPassenger(valueOutput) ? valueOutput.buildResult() : null;
                    if (saved != null) {
                        saved.remove("Dimension");
                        tag = tag.merge(saved);
                    }
                }
            }

            if (!tag.contains("id")) continue;

            AtomicBoolean useNewUuid = new AtomicBoolean(true);

            Entity spawned = EntityType.loadEntityRecursive(tag, serverLevel, EntitySpawnReason.COMMAND, entity -> {
                if (!this.plugin.canEntityBeManipulated(entity.getType())) {
                    return null;
                }

                if (useNewUuid.getAndSet(false)) {
                    entity.setUUID(entry.newUuid);
                } else {
                    entity.setUUID(UUID.randomUUID());
                }

                if (entity instanceof HangingEntity hangingEntity) {
                    float changedYaw = entry.yaw - entity.getYRot();
                    int rotations = Math.round(changedYaw / 90);
                    hangingEntity.rotate(ROTATION_VALUES[rotations & 3]);

                    if (entity instanceof ItemFrame itemFrame && itemFrame.getDirection().getAxis() == Direction.Axis.Y) {
                        itemFrame.setRotation(itemFrame.getRotation() - Math.round(changedYaw / 45));
                    }
                }

                entity.snapTo(position.x, position.y, position.z, entry.yaw, entry.pitch);
                entity.setYHeadRot(entity.getYRot());

                return entity;
            });

            if (spawned != null) {
                if (serverLevel.tryAddFreshEntityWithPassengers(spawned)) {
                    AxiomSpawnEntityEvent spawnEntityEvent = new AxiomSpawnEntityEvent(player, spawned.getBukkitEntity());
                    Bukkit.getPluginManager().callEvent(spawnEntityEvent);
                    if (spawnEntityEvent.isCancelled() || spawned.isRemoved()) {
                        for (Entity passenger : spawned.getIndirectPassengers()) {
                            passenger.discard();
                        }
                        spawned.discard();
                    }
                }
            }
        }
    }

}
