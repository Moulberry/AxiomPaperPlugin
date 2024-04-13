package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.NbtSanitization;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpawnEntityPacketListener implements PluginMessageListener {

    private final AxiomPaper plugin;
    public SpawnEntityPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    private record SpawnEntry(UUID newUuid, double x, double y, double z, float yaw, float pitch,
                              @Nullable UUID copyFrom, CompoundTag tag) {
        public SpawnEntry(FriendlyByteBuf friendlyByteBuf) {
            this(friendlyByteBuf.readUUID(), friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble(),
                friendlyByteBuf.readDouble(), friendlyByteBuf.readFloat(), friendlyByteBuf.readFloat(),
                friendlyByteBuf.readNullable(FriendlyByteBuf::readUUID), friendlyByteBuf.readNbt());
        }
    }

    private static final Rotation[] ROTATION_VALUES = Rotation.values();

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!this.plugin.canUseAxiom(player)) {
            return;
        }

        if (!player.hasPermission("axiom.entity.*") && !player.hasPermission("axiom.entity.spawn")) {
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        List<SpawnEntry> entries = friendlyByteBuf.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 1000), SpawnEntry::new);

        ServerLevel serverLevel = ((CraftWorld)player.getWorld()).getHandle();

        List<String> whitelistedEntities = this.plugin.configuration.getStringList("whitelist-entities");
        List<String> blacklistedEntities = this.plugin.configuration.getStringList("blacklist-entities");

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
                    CompoundTag compoundTag = new CompoundTag();
                    if (entityCopyFrom.saveAsPassenger(compoundTag)) {
                        compoundTag.remove("Dimension");
                        tag = tag.merge(compoundTag);
                    }
                }
            }

            if (!tag.contains("id")) continue;

            AtomicBoolean useNewUuid = new AtomicBoolean(true);

            Entity spawned = EntityType.loadEntityRecursive(tag, serverLevel, entity -> {
                String type = EntityType.getKey(entity.getType()).toString();
                if (!whitelistedEntities.isEmpty() && !whitelistedEntities.contains(type)) return null;
                if (blacklistedEntities.contains(type)) return null;

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

                entity.moveTo(position.x, position.y, position.z, entry.yaw, entry.pitch);
                entity.setYHeadRot(entity.getYRot());

                return entity;
            });

            if (spawned != null) {
                serverLevel.tryAddFreshEntityWithPassengers(spawned);
            }
        }
    }

}
