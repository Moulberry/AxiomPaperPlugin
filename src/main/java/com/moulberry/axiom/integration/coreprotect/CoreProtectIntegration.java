package com.moulberry.axiom.integration.coreprotect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.CraftWorld;

public class CoreProtectIntegration {
    public static boolean isEnabled() {
        return CoreProtectIntegrationImpl.isEnabled();
    }
    
    public static boolean logPlacement(String name, BlockState blockState, CraftWorld world, BlockPos pos) {
        if (!CoreProtectIntegrationImpl.isEnabled()) {
            return false;
        }

        return CoreProtectIntegrationImpl.logPlacement(name, blockState, world, pos);
    }

    public static boolean logPlacement(String name, BlockState blockState, CraftWorld world, int x, int y, int z) {
        if (!CoreProtectIntegrationImpl.isEnabled()) {
            return false;
        }
        
        return CoreProtectIntegrationImpl.logPlacement(name, blockState, world, x, y, z);
    }

    public static boolean logPlacement(String name, Level level, CraftWorld world, BlockPos pos) {
        if (!CoreProtectIntegrationImpl.isEnabled()) {
            return false;
        }

        return CoreProtectIntegrationImpl.logPlacement(name, level, world, pos);
    }

    public static boolean logRemoval(String name, BlockState blockState, CraftWorld world, BlockPos pos) {
        if (!CoreProtectIntegrationImpl.isEnabled()) {
            return false;
        }

        return CoreProtectIntegrationImpl.logRemoval(name, blockState, world, pos);
    }

    public static boolean logRemoval(String name, BlockState blockState, CraftWorld world, int x, int y, int z) {
        if (!CoreProtectIntegrationImpl.isEnabled()) {
            return false;
        }

        return CoreProtectIntegrationImpl.logRemoval(name, blockState, world, x, y, z);
    }

    public static boolean logRemoval(String name, Level level, CraftWorld world, BlockPos pos) {
        if (!CoreProtectIntegrationImpl.isEnabled()) {
            return false;
        }

        return CoreProtectIntegrationImpl.logRemoval(name, level, world, pos);
    }
}