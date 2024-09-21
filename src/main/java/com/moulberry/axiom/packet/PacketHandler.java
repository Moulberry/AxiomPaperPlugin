package com.moulberry.axiom.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;

public interface PacketHandler {

    default boolean handleAsync() {
        return false;
    }

    void onReceive(Player player, FriendlyByteBuf friendlyByteBuf);

}
