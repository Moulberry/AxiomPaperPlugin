package com.moulberry.axiom.viaversion;

import com.moulberry.axiom.AxiomPaper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.entity.Player;

public class UnknownVersionHelper {

    public static void skipTagUnknown(FriendlyByteBuf friendlyByteBuf, Player player) {
        if (AxiomPaper.PLUGIN.isMismatchedDataVersion(player.getUniqueId())) {
            try {
                ViaVersionHelper.skipTagViaVersion(friendlyByteBuf, player);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            friendlyByteBuf.readNbt(); // Discard
        }
    }

    public static CompoundTag readTagUnknown(FriendlyByteBuf friendlyByteBuf, Player player) {
        if (AxiomPaper.PLUGIN.isMismatchedDataVersion(player.getUniqueId())) {
            try {
                return ViaVersionHelper.readTagViaVersion(friendlyByteBuf, player);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return friendlyByteBuf.readNbt();
        }
    }

}
