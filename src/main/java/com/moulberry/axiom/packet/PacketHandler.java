package com.moulberry.axiom.packet;

import net.minecraft.network.FriendlyByteBuf;
<<<<<<< HEAD
import net.minecraft.server.level.ServerPlayer;
=======
>>>>>>> 2a573eb (Protocol rework)
import org.bukkit.entity.Player;

public interface PacketHandler {

    default boolean handleAsync() {
        return false;
    }

    void onReceive(Player player, FriendlyByteBuf friendlyByteBuf);

}
