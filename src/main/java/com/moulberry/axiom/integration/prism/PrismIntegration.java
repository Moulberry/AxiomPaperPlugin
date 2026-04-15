package com.moulberry.axiom.integration.prism;

import com.moulberry.axiom.AxiomPaper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class PrismIntegration {
    public static boolean isEnabled() {
        return PrismIntegrationImpl.isEnabled() && AxiomPaper.PLUGIN.shouldLogPrism(PrismLoggingType.BLOCK_CHANGES);
    }

    public static void logChange(Player player, BlockState oldBlockState, @Nullable String oldBlockEntityNbt,
                                 BlockState newBlockState, @Nullable String newBlockEntityNbt, CraftWorld world, BlockPos pos) {
        if (!isEnabled()) {
            return;
        }

        PrismIntegrationImpl.logChange(player, oldBlockState, oldBlockEntityNbt, newBlockState, newBlockEntityNbt, world, pos);
    }

}
