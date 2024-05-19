package com.moulberry.axiom.integration.coreprotect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;

public class CoreProtectIntegration {
    public static boolean isEnabled() {
        return CoreProtectIntegrationImpl.isEnabled();
    }

    public static void logPlacement(String name, BlockState blockState, CraftWorld world, BlockPos pos) {
        if (!CoreProtectIntegrationImpl.isEnabled()) {
            return;
        }

        CoreProtectIntegrationImpl.logPlacement(name, blockState, world, pos);
    }

    public static void logRemoval(String name, BlockState blockState, CraftWorld world, BlockPos pos) {
        if (!CoreProtectIntegrationImpl.isEnabled()) {
            return;
        }

        CoreProtectIntegrationImpl.logRemoval(name, blockState, world, pos);
    }

}
