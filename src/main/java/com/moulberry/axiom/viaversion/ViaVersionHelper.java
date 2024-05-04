package com.moulberry.axiom.viaversion;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.buffer.BlockBuffer;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.data.Mappings;
import com.viaversion.viaversion.api.protocol.ProtocolPathEntry;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.IdMapper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.entity.Player;

import java.util.List;

public class ViaVersionHelper {

    private static final Int2ObjectOpenHashMap<IdMapper<BlockState>> blockRegistryCache = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<String> blockRegistryErrorCache = new Int2ObjectOpenHashMap<>();

    public static IdMapper<BlockState> getBlockRegistryForVersion(IdMapper<BlockState> mapper, int playerVersion) {
        if (blockRegistryErrorCache.containsKey(playerVersion)) {
            throw new RuntimeException(blockRegistryErrorCache.get(playerVersion));
        }
        if (blockRegistryCache.containsKey(playerVersion)) {
            return blockRegistryCache.get(playerVersion);
        }

        List<ProtocolPathEntry> path = Via.getManager().getProtocolManager().getProtocolPath(playerVersion,
            SharedConstants.getProtocolVersion());

        if (path == null) {
            blockRegistryErrorCache.put(playerVersion, "Failed to find protocol path");
            throw new RuntimeException("Failed to find protocol path");
        }

        for (int i = path.size()-1; i >= 0; i--) {
            ProtocolPathEntry protocolPathEntry = path.get(i);

            MappingData mappingData = protocolPathEntry.protocol().getMappingData();

            if (mappingData == null) {
                blockRegistryErrorCache.put(playerVersion, "Failed to load mapping data (" + protocolPathEntry + ")");
                throw new RuntimeException("Failed to load mapping data (" + protocolPathEntry + ")");
            }

            Mappings blockStateMappings = mappingData.getBlockStateMappings();

            if (blockStateMappings == null) {
                blockRegistryErrorCache.put(playerVersion, "Failed to load BlockState mappings (" + protocolPathEntry + ")");
                throw new RuntimeException("Failed to load BlockState mappings (" + protocolPathEntry + ")");
            }

            mapper = ViaVersionHelper.applyMappings(mapper, blockStateMappings);
        }

        blockRegistryCache.put(playerVersion, mapper);
        return mapper;
    }

    public static IdMapper<BlockState> applyMappings(IdMapper<BlockState> registry, Mappings mappings) {
        IdMapper<BlockState> newBlockRegistry = new IdMapper<>();

        // Add empty mappings for non-existent blocks
        int size = mappings.mappedSize();
        for (int i = 0; i < size; i++) {
            newBlockRegistry.addMapping(BlockBuffer.EMPTY_STATE, i);
        }

        // Map blocks
        for (int i = 0; i < registry.size(); i++) {
            BlockState blockState = registry.byId(i);

            if (blockState != null) {
                int newId = mappings.getNewId(i);
                if (newId >= 0) {
                    newBlockRegistry.addMapping(blockState, newId);
                }
            }
        }

        // Ensure block -> id is correct for the empty state
        int newEmptyStateId = mappings.getNewId(registry.getId(BlockBuffer.EMPTY_STATE));
        if (newEmptyStateId >= 0) {
            newBlockRegistry.addMapping(BlockBuffer.EMPTY_STATE, newEmptyStateId);
        }

        return newBlockRegistry;
    }

    private static final int UNNAMED_COMPOUND_TAG_CHANGE_VERSION = ProtocolVersion.v1_20_2.getVersion();

    public static void skipTagUnknown(FriendlyByteBuf friendlyByteBuf, Player player) {
        if (AxiomPaper.PLUGIN.isMismatchedDataVersion(player.getUniqueId())) {
            int playerVersion = Via.getAPI().getPlayerVersion(player.getUniqueId());
            try {
                ViaVersionHelper.skipTagViaVersion(friendlyByteBuf, playerVersion);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            friendlyByteBuf.readNbt(); // Discard
        }
    }

    public static CompoundTag readTagUnknown(FriendlyByteBuf friendlyByteBuf, Player player) {
        if (AxiomPaper.PLUGIN.isMismatchedDataVersion(player.getUniqueId())) {
            int playerVersion = Via.getAPI().getPlayerVersion(player.getUniqueId());
            try {
                return ViaVersionHelper.readTagViaVersion(friendlyByteBuf, playerVersion);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return friendlyByteBuf.readNbt();
        }
    }

    public static void skipTagViaVersion(FriendlyByteBuf friendlyByteBuf, int playerVersion) throws Exception {
        getTagType(playerVersion).read(friendlyByteBuf);
    }

    public static CompoundTag readTagViaVersion(FriendlyByteBuf friendlyByteBuf, int playerVersion) throws Exception {
        Type<com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag> from = getTagType(playerVersion);
        Type<com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag> to = getTagType(SharedConstants.getProtocolVersion());

        return readTagViaVersion(friendlyByteBuf, from, to);
    }

    private static Type<com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag> getTagType(int version) {
        if (version < UNNAMED_COMPOUND_TAG_CHANGE_VERSION) {
            return Type.NAMED_COMPOUND_TAG;
        } else {
            return Type.COMPOUND_TAG;
        }
    }

    private static CompoundTag readTagViaVersion(FriendlyByteBuf friendlyByteBuf,
            Type<com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag> from,
            Type<com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag> to) throws Exception {
        if (from == to) {
            return friendlyByteBuf.readNbt();
        }

        com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag tag = from.read(friendlyByteBuf);
        ByteBuf buffer = Unpooled.buffer();
        to.write(buffer, tag);
        return new FriendlyByteBuf(buffer).readNbt();
    }

}
