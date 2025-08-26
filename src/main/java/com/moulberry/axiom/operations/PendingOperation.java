package com.moulberry.axiom.operations;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface PendingOperation {

    boolean isFinished();
    void tick(ServerLevel level);
    ServerPlayer executor();

}
