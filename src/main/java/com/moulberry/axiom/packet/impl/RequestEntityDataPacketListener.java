package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.packet.PacketHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;

import java.util.*;

public class RequestEntityDataPacketListener implements PacketHandler {

    public static final ResourceLocation RESPONSE_ID = VersionHelper.createResourceLocation("axiom:response_entity_data");

    private final AxiomPaper plugin;
    private final boolean forceFail;
    public RequestEntityDataPacketListener(AxiomPaper plugin, boolean forceFail) {
        this.plugin = plugin;
        this.forceFail = forceFail;
    }

    @Override
    public void onReceive(org.bukkit.entity.Player bukkitPlayer, RegistryFriendlyByteBuf friendlyByteBuf) {
        ServerPlayer player = ((CraftPlayer)bukkitPlayer).getHandle();
        long id = friendlyByteBuf.readLong();

        if (this.forceFail || !this.plugin.canUseAxiom(bukkitPlayer, "axiom.entity.request_data") || this.plugin.isMismatchedDataVersion(bukkitPlayer.getUniqueId())) {
            // We always send an 'empty' response in order to make the client happy
            sendResponse(player, id, true, Map.of());
            return;
        }

        if (!this.plugin.canModifyWorld(bukkitPlayer, bukkitPlayer.getWorld())) {
            sendResponse(player, id, true, Map.of());
            return;
        }

        List<UUID> request = friendlyByteBuf.readCollection(this.plugin.limitCollection(ArrayList::new), buf -> buf.readUUID());
        ServerLevel serverLevel = player.serverLevel();

        final int maxPacketSize = 0x100000;
        int remainingBytes = maxPacketSize;

        Map<UUID, CompoundTag> entityData = new HashMap<>();

        Set<UUID> visitedEntities = new HashSet<>();

        for (UUID uuid : request) {
            if (!visitedEntities.add(uuid)) {
                continue;
            }

            Entity entity = serverLevel.getEntity(uuid);
            if (entity == null || entity instanceof Player) {
                continue;
            }

            if (!this.plugin.canEntityBeManipulated(entity.getType())) {
                continue;
            }

            if (!Integration.canPlaceBlock(bukkitPlayer, new Location(bukkitPlayer.getWorld(),
                    entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()))) {
                continue;
            }

            CompoundTag entityTag = new CompoundTag();
            if (entity.save(entityTag)) {
                int size = entityTag.sizeInBytes();
                if (size >= maxPacketSize) {
                    sendResponse(player, id, false, Map.of(uuid, entityTag));
                    continue;
                }

                // Send partial packet if we've run out of available bytes
                if (remainingBytes - size < 0) {
                    sendResponse(player, id, false, entityData);
                    entityData.clear();
                    remainingBytes = maxPacketSize;
                }

                entityData.put(uuid, entityTag);
                remainingBytes -= size;
            }
        }

        sendResponse(player, id, true, entityData);
    }

    private static void sendResponse(ServerPlayer player, long id, boolean finished, Map<UUID, CompoundTag> map) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        friendlyByteBuf.writeLong(id);
        friendlyByteBuf.writeBoolean(finished);
        friendlyByteBuf.writeMap(map, (buf, uuid) -> buf.writeUUID(uuid), (buf, nbt) -> buf.writeNbt(nbt));

        byte[] bytes = new byte[friendlyByteBuf.writerIndex()];
        friendlyByteBuf.getBytes(0, bytes);
        VersionHelper.sendCustomPayload(player, RESPONSE_ID, bytes);
    }

}
