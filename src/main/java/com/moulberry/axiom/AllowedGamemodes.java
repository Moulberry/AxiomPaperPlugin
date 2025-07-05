package com.moulberry.axiom;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.GameType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class AllowedGamemodes {
    private EnumSet<GameType> gameTypes = EnumSet.allOf(GameType.class);

    public AllowedGamemodes(@Nullable List<String> strings) {
        if (strings == null) return;

        if (!strings.isEmpty()) {
            gameTypes = EnumSet.copyOf(strings.stream()
                    .map(GameType::byName)
                    .toList());
        } else {
            gameTypes = EnumSet.noneOf(GameType.class);
        }
    }

    public void send(Player player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnumSet(gameTypes, GameType.class);

        byte[] bytes = ByteBufUtil.getBytes(buf);
        VersionHelper.sendCustomPayload(player, "axiom:allowed_gamemodes", bytes);
    }
}
