package com.moulberry.axiom.integration.changelog;

import com.moulberry.axiom.integration.coreprotect.CoreProtectIntegration;
import com.moulberry.axiom.integration.prism.PrismIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ChangeLogIntegration {

    private ChangeLogIntegration() {}

    public static boolean isEnabled() {
        return CoreProtectIntegration.isEnabled() || PrismIntegration.isEnabled();
    }

    public static void logChange(Player player, BlockState oldBlockState, @Nullable String oldBlockEntityNbt,
                                 BlockState newBlockState, @Nullable String newBlockEntityNbt, CraftWorld world, BlockPos pos) {
        boolean blockStateChanged = oldBlockState != newBlockState;
        if (!blockStateChanged && Objects.equals(oldBlockEntityNbt, newBlockEntityNbt)) {
            return;
        }

        if (blockStateChanged && !oldBlockState.isAir()) {
            CoreProtectIntegration.logRemoval(player.getName(), oldBlockState, world, pos);
        }
        if (blockStateChanged && !newBlockState.isAir()) {
            CoreProtectIntegration.logPlacement(player.getName(), newBlockState, world, pos);
        }

        PrismIntegration.logChange(player, oldBlockState, oldBlockEntityNbt, newBlockState, newBlockEntityNbt, world, pos);
    }

}
