package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.NbtSanitization;
import com.moulberry.axiom.event.AxiomManipulateEntityEvent;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.viaversion.UnknownVersionHelper;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
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
import java.util.Set;
import java.util.UUID;

public class ManipulateEntityPacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public ManipulateEntityPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    public enum PassengerManipulation {
        NONE,
        REMOVE_ALL,
        ADD_LIST,
        REMOVE_LIST
    }

    public record ManipulateEntry(UUID uuid, @Nullable Set<RelativeMovement> relativeMovementSet, @Nullable Vec3 position,
                                  float yaw, float pitch, CompoundTag merge, PassengerManipulation passengerManipulation, List<UUID> passengers) {
        public static ManipulateEntry read(FriendlyByteBuf friendlyByteBuf, Player player, AxiomPaper plugin) {
            UUID uuid = friendlyByteBuf.readUUID();

            int flags = friendlyByteBuf.readByte();
            Set<RelativeMovement> relativeMovementSet = null;
            Vec3 position = null;
            float yaw = 0;
            float pitch = 0;
            if (flags >= 0) {
                relativeMovementSet = RelativeMovement.unpack(flags);
                position = new Vec3(friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble());
                yaw = friendlyByteBuf.readFloat();
                pitch = friendlyByteBuf.readFloat();
            }

            CompoundTag nbt = UnknownVersionHelper.readTagUnknown(friendlyByteBuf, player);

            PassengerManipulation passengerManipulation = friendlyByteBuf.readEnum(PassengerManipulation.class);
            List<UUID> passengers = List.of();
            if (passengerManipulation == PassengerManipulation.ADD_LIST || passengerManipulation == PassengerManipulation.REMOVE_LIST) {
                passengers = friendlyByteBuf.readCollection(plugin.limitCollection(ArrayList::new), FriendlyByteBuf::readUUID);
            }

            return new ManipulateEntry(uuid, relativeMovementSet, position, yaw, pitch, nbt,
                passengerManipulation, passengers);
        }
    }

    private static final Rotation[] ROTATION_VALUES = Rotation.values();

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, "axiom.entity.manipulate", true)) {
            return;
        }

        if (!this.plugin.canModifyWorld(player, player.getWorld())) {
            return;
        }

        List<ManipulateEntry> entries = friendlyByteBuf.readCollection(this.plugin.limitCollection(ArrayList::new),
                buf -> ManipulateEntry.read(buf, player, this.plugin));

        ServerLevel serverLevel = ((CraftWorld)player.getWorld()).getHandle();

        for (ManipulateEntry entry : entries) {
            Entity entity = serverLevel.getEntity(entry.uuid);
            if (entity == null || entity instanceof net.minecraft.world.entity.player.Player || entity.hasPassenger(ManipulateEntityPacketListener::isPlayer)) continue;

            if (!this.plugin.canEntityBeManipulated(entity.getType())) {
                continue;
            }

            Vec3 position = entity.position();
            BlockPos containing = BlockPos.containing(position.x, position.y, position.z);

            if (!Integration.canPlaceBlock(player, new Location(player.getWorld(),
                    containing.getX(), containing.getY(), containing.getZ()))) {
                continue;
            }

            AxiomManipulateEntityEvent manipulateEvent = new AxiomManipulateEntityEvent(player, entity.getBukkitEntity());
            if (!manipulateEvent.callEvent()) {
                continue;
            }

            if (entry.merge != null && !entry.merge.isEmpty()) {
                NbtSanitization.sanitizeEntity(entry.merge);

                CompoundTag compoundTag = entity.saveWithoutId(new CompoundTag());
                compoundTag = merge(compoundTag, entry.merge);
                entity.load(compoundTag);
            }

            entity.setPosRaw(position.x, position.y, position.z);

            Vec3 entryPos = entry.position();
            if (entryPos != null && entry.relativeMovementSet != null) {
                double newX = entry.relativeMovementSet.contains(RelativeMovement.X) ? entity.position().x + entryPos.x : entryPos.x;
                double newY = entry.relativeMovementSet.contains(RelativeMovement.Y) ? entity.position().y + entryPos.y : entryPos.y;
                double newZ = entry.relativeMovementSet.contains(RelativeMovement.Z) ? entity.position().z + entryPos.z : entryPos.z;
                float newYaw = entry.relativeMovementSet.contains(RelativeMovement.Y_ROT) ? entity.getYRot() + entry.yaw : entry.yaw;
                float newPitch = entry.relativeMovementSet.contains(RelativeMovement.X_ROT) ? entity.getXRot() + entry.pitch : entry.pitch;

                if (entity instanceof HangingEntity hangingEntity) {
                    float changedYaw = newYaw - entity.getYRot();
                    int rotations = Math.round(changedYaw / 90);
                    hangingEntity.rotate(ROTATION_VALUES[rotations & 3]);

                    if (entity instanceof ItemFrame itemFrame && itemFrame.getDirection().getAxis() == Direction.Axis.Y) {
                        itemFrame.setRotation(itemFrame.getRotation() - Math.round(changedYaw / 45));
                    }
                }

                containing = BlockPos.containing(newX, newY, newZ);

                if (Integration.canPlaceBlock(player, new Location(player.getWorld(),
                        containing.getX(), containing.getY(), containing.getZ()))) {
                    entity.teleportTo(serverLevel, newX, newY, newZ, Set.of(), newYaw, newPitch);
                }

                entity.setYHeadRot(newYaw);
            }

            switch (entry.passengerManipulation) {
                case NONE -> {}
                case REMOVE_ALL -> entity.ejectPassengers();
                case ADD_LIST -> {
                    for (UUID passengerUuid : entry.passengers) {
                        Entity passenger = serverLevel.getEntity(passengerUuid);

                        if (passenger == null || passenger.isPassenger() ||
                            passenger instanceof net.minecraft.world.entity.player.Player || passenger.hasPassenger(ManipulateEntityPacketListener::isPlayer)) continue;

                        if (!this.plugin.canEntityBeManipulated(passenger.getType())) {
                            continue;
                        }

                        // Prevent mounting loop
                        if (passenger.getSelfAndPassengers().anyMatch(entity2 -> entity2 == entity)) {
                            continue;
                        }

                        position = passenger.position();
                        containing = BlockPos.containing(position.x, position.y, position.z);

                        if (!Integration.canPlaceBlock(player, new Location(player.getWorld(),
                                containing.getX(), containing.getY(), containing.getZ()))) {
                            continue;
                        }

                        passenger.startRiding(entity, true);
                    }
                }
                case REMOVE_LIST -> {
                    for (UUID passengerUuid : entry.passengers) {
                        Entity passenger = serverLevel.getEntity(passengerUuid);
                        if (passenger == null || passenger == entity || passenger instanceof net.minecraft.world.entity.player.Player ||
                            passenger.hasPassenger(ManipulateEntityPacketListener::isPlayer)) continue;

                        if (!this.plugin.canEntityBeManipulated(passenger.getType())) {
                            continue;
                        }

                        Entity vehicle = passenger.getVehicle();
                        if (vehicle == entity) {
                            passenger.stopRiding();
                        }
                    }
                }
            }
        }
    }

    private static CompoundTag merge(CompoundTag left, CompoundTag right) {
        if (right.contains("axiom:modify")) {
            right.remove("axiom:modify");
            return right;
        }

        for (String key : right.getAllKeys()) {
            Tag tag = right.get(key);
            if (tag instanceof CompoundTag compound) {
                if (compound.isEmpty()) {
                    left.remove(key);
                } else if (left.contains(key, Tag.TAG_COMPOUND)) {
                    CompoundTag child = left.getCompound(key);
                    child = merge(child, compound);
                    left.put(key, child);
                } else {
                    CompoundTag copied = compound.copy();
                    if (copied.contains("axiom:modify")) {
                        copied.remove("axiom:modify");
                    }
                    left.put(key, copied);
                }
            } else {
                left.put(key, tag.copy());
            }
        }
        return left;
    }

    private static boolean isPlayer(Entity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player;
    }

}
