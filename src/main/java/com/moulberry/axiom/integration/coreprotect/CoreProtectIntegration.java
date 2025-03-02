package com.moulberry.axiom.integration.coreprotect;

import com.moulberry.axiom.AxiomPaper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.CraftWorld;

public class CoreProtectIntegration {
    public static boolean isEnabled() {
        return CoreProtectIntegrationImpl.isEnabled();
    }

    public static void logPlacement(String name, BlockState blockState, CraftWorld world, BlockPos pos) {
        if (!CoreProtectIntegrationImpl.isEnabled() || !AxiomPaper.PLUGIN.logCoreProtectChanges) {
            return;
        }

        CoreProtectIntegrationImpl.logPlacement(name, blockState, world, pos);
    }

    public static void logRemoval(String name, BlockState blockState, CraftWorld world, BlockPos pos) {
        if (!CoreProtectIntegrationImpl.isEnabled() || !AxiomPaper.PLUGIN.logCoreProtectChanges) {
            return;
        }

        CoreProtectIntegrationImpl.logRemoval(name, blockState, world, pos);
    }

}
