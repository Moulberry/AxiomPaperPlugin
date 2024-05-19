package com.moulberry.axiom.integration.coreprotect;

import com.moulberry.axiom.AxiomPaper;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class CoreProtectIntegrationImpl {
    private static final CoreProtectAPI COREPROTECT_API;
    private static final boolean COREPROTECT_ENABLED;
    private static final Constructor<CraftBlockState> CRAFT_BLOCK_STATE_CONSTRUCTOR;

    static {
        COREPROTECT_API = getCoreProtect();
        Constructor<CraftBlockState> constructor = null;

        if (COREPROTECT_API != null) {
            try {
                constructor = CraftBlockState.class.getDeclaredConstructor(World.class, BlockPos.class, BlockState.class);
                constructor.setAccessible(true);
            } catch (NoSuchMethodException | SecurityException e) {
                AxiomPaper.PLUGIN.getLogger().warning("Failed to get CraftBlockState constructor for CoreProtect: " + e);
            }
        }

        CRAFT_BLOCK_STATE_CONSTRUCTOR = constructor;
        COREPROTECT_ENABLED = COREPROTECT_API != null && CRAFT_BLOCK_STATE_CONSTRUCTOR != null;
    }

    private static CoreProtectAPI getCoreProtect() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");

        if (plugin == null || !(plugin instanceof CoreProtect)) {
            return null;
        }

        CoreProtectAPI coreProtect = ((CoreProtect) plugin).getAPI();
        if (coreProtect.isEnabled() == false) {
            return null;
        }

        if (coreProtect.APIVersion() < 10) {
            return null;
        }

        return coreProtect;
    }

    private static CraftBlockState createCraftBlockState(World world, BlockPos pos, BlockState blockState) {
        try {
            return (CraftBlockState) CRAFT_BLOCK_STATE_CONSTRUCTOR.newInstance(world, pos, blockState);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to create CraftBlockState for CoreProtect: " + e);
            return null;
        }
    }

    static boolean isEnabled() {
        return COREPROTECT_ENABLED;
    }

    static boolean logPlacement(String name, BlockState blockState, CraftWorld world, BlockPos pos) {
        if (blockState.isAir()) {
            return false;
        }

        return COREPROTECT_API.logPlacement(name, createCraftBlockState(world, pos, blockState));
    }

    static boolean logPlacement(String name, BlockState blockState, CraftWorld world, int x, int y, int z) {
        return logPlacement(name, blockState, world, new BlockPos(x, y, z));
    }

    static boolean logPlacement(String name, Level level, CraftWorld world, BlockPos pos) {
        return logPlacement(name, level.getBlockState(pos), world, pos);
    }

    static boolean logRemoval(String name, BlockState blockState, CraftWorld world, BlockPos pos) {
        return COREPROTECT_API.logRemoval(name, createCraftBlockState(world, pos, blockState));
    }

    static boolean logRemoval(String name, BlockState blockState, CraftWorld world, int x, int y, int z) {
        return logRemoval(name, blockState, world, new BlockPos(x, y, z));
    }

    static boolean logRemoval(String name, Level level, CraftWorld world, BlockPos pos) {
        return logRemoval(name, level.getBlockState(pos), world, pos);
    }
}