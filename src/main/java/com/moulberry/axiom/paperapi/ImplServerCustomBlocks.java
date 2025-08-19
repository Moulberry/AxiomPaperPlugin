package com.moulberry.axiom.paperapi;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public class ImplServerCustomBlocks {

    private static final Map<ResourceLocation, ImplAxiomCustomBlock> registeredBlocks = new LinkedHashMap<>();
    private static final Map<BlockState, ResourceLocation> registeredBlockStates = new HashMap<>();

    public static void register(ImplAxiomCustomBlock customBlock) throws AxiomAlreadyRegisteredException {
        if (!MinecraftServer.getServer().isSameThread()) {
            throw new WrongThreadException();
        }

        // Validate
        validateCorrectNumberOfStates(customBlock.properties(), customBlock.blocks().size());
        Set<BlockState> blockStateSet = new HashSet<>(customBlock.blocks());
        checkMappingContainedInSet(customBlock.rotateYMappings(), blockStateSet, "customRotateY");
        checkMappingContainedInSet(customBlock.flipXMappings(), blockStateSet, "customFlipX");
        checkMappingContainedInSet(customBlock.flipYMappings(), blockStateSet, "customFlipY");
        checkMappingContainedInSet(customBlock.flipZMappings(), blockStateSet, "customFlipZ");

        // Check for duplicate registration
        if (registeredBlocks.containsKey(customBlock.id())) {
            throw new AxiomAlreadyRegisteredException("Custom block is already registered with id " + customBlock.id());
        }
        for (BlockState block : customBlock.blocks()) {
            ResourceLocation existingId = registeredBlockStates.get(block);
            if (existingId != null) {
                throw new AxiomAlreadyRegisteredException("BlockState " + block + " is already used by " + existingId);
            }
        }

        // Register
        registeredBlocks.put(customBlock.id(), customBlock);
        for (BlockState block : customBlock.blocks()) {
            registeredBlockStates.put(block, customBlock.id());
        }

        // Send
        byte[] registerPacketData = null;
        byte[] registerPacketDataMismatch = null;
        for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            if (AxiomPaper.PLUGIN.canUseAxiom(player.getBukkitEntity())) {
                int playerProtocolVersion = AxiomPaper.PLUGIN.getProtocolVersionFor(player.getUUID());
                boolean protocolMismatch = playerProtocolVersion != SharedConstants.getProtocolVersion();
                if (!protocolMismatch) {
                    if (registerPacketDataMismatch == null) {
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
                        customBlock.write(buf, true);
                        registerPacketDataMismatch = ByteBufUtil.getBytes(buf);
                    }
                    VersionHelper.sendCustomPayload(player, "axiom:register_custom_block_v2", registerPacketDataMismatch);
                } else {
                    if (registerPacketData == null) {
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
                        customBlock.write(buf, false);
                        registerPacketData = ByteBufUtil.getBytes(buf);
                    }
                    VersionHelper.sendCustomPayload(player, "axiom:register_custom_block_v2", registerPacketData);
                }
            }
        }
    }

    public static void sendAll(ServerPlayer player) {
        // Clear any existing custom blocks
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        buf.writeVarInt(0);
        VersionHelper.sendCustomPayload(player, "axiom:custom_blocks", ByteBufUtil.getBytes(buf));

        // Send all custom blocks
        int playerProtocolVersion = AxiomPaper.PLUGIN.getProtocolVersionFor(player.getUUID());
        boolean protocolMismatch = playerProtocolVersion != SharedConstants.getProtocolVersion();
        for (ImplAxiomCustomBlock customBlock : registeredBlocks.values()) {
            System.out.println("sending block: " + customBlock.id());
            buf.clear();
            customBlock.write(buf, protocolMismatch);
            byte[] bytes = ByteBufUtil.getBytes(buf);
            VersionHelper.sendCustomPayload(player, "axiom:register_custom_block_v2", bytes);
        }
    }

    private static void checkMappingContainedInSet(Map<BlockState, BlockState> mappings, Set<BlockState> vanillaStatesSet, String name) {
        for (Map.Entry<BlockState, BlockState> entry : mappings.entrySet()) {
            if (!vanillaStatesSet.contains(entry.getKey()) && !vanillaStatesSet.contains(entry.getValue())) {
                throw new RuntimeException(name + " mapping either key or value must be part of custom block");
            }
        }
    }

    private static void validateCorrectNumberOfStates(List<AxiomProperty> properties, int numBlocks) {
        int expectedStates = 1;
        for (AxiomProperty property : properties) {
            expectedStates *= property.numValues();
        }
        if (expectedStates != numBlocks) {
            StringBuilder error = new StringBuilder();
            error.append("Incorrect number of blocks provided, needed ");
            error.append(expectedStates);
            error.append(", got ");
            error.append(numBlocks);
            error.append(": ");

            if (properties.isEmpty()) {
                error.append("no properties ");
            } else {
                boolean first = true;
                for (AxiomProperty property : properties) {
                    if (first) {
                        first = false;
                    } else {
                        error.append("* ");
                    }
                    error.append(property.name());
                    error.append(" (");
                    error.append(property.numValues());
                    error.append(") ");
                }
            }
            error.append(" = ").append(expectedStates).append(" block(s)");

            throw new RuntimeException(error.toString());
        }
    }

}
