package com.moulberry.axiom.viaversion;

import com.moulberry.axiom.AxiomPaper;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.entity.Player;

public class UnknownVersionHelper {

    public static CompoundTag readTagUnknown(FriendlyByteBuf friendlyByteBuf, Player player) {
        int playerProtocolVersion = AxiomPaper.PLUGIN.getProtocolVersionFor(player.getUniqueId());
        if (playerProtocolVersion != SharedConstants.getProtocolVersion()) {
            return ViaVersionHelper.readTagViaVersion(friendlyByteBuf, playerProtocolVersion);
        } else {
            return friendlyByteBuf.readNbt();
        }
    }

    public static void readPalettedContainerUnknown(FriendlyByteBuf friendlyByteBuf, PalettedContainer<BlockState> palettedContainer, Player player) {
        int playerProtocolVersion = AxiomPaper.PLUGIN.getProtocolVersionFor(player.getUniqueId());
        if (playerProtocolVersion != SharedConstants.getProtocolVersion()) {
            ViaVersionHelper.readPalettedContainerViaVersion(friendlyByteBuf, palettedContainer, playerProtocolVersion);
        } else {
            palettedContainer.read(friendlyByteBuf);
        }
    }

}
