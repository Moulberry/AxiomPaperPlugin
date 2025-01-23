package com.moulberry.axiom.integration;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.integration.worldguard.WorldGuardIntegration;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class Integration {

    // todo: test if all this is working for both plotsqured, worldguard, plotsquared+worldguard

    public static boolean canBreakBlock(Player player, Block block) {

        // PATCH_d76d9d98-4301-45d0-a6b6-f713ea4e916d_START
        final var rokianPermsRegionEditApi = AxiomPaper.PLUGIN.getRokianPermsRegionEditApi();
        if(rokianPermsRegionEditApi != null) {
            return rokianPermsRegionEditApi.canBreakBlock(player, block);
        }
        // PATCH_d76d9d98-4301-45d0-a6b6-f713ea4e916d_END

        return PlotSquaredIntegration.canBreakBlock(player, block) && WorldGuardIntegration.canBreakBlock(player, block.getLocation());
    }

    public static boolean canPlaceBlock(Player player, org.bukkit.Location loc) {

        // PATCH_d76d9d98-4301-45d0-a6b6-f713ea4e916d_START
        final var rokianPermsRegionEditApi = AxiomPaper.PLUGIN.getRokianPermsRegionEditApi();
        if(rokianPermsRegionEditApi != null) {
            return rokianPermsRegionEditApi.canPlaceBlock(player, loc);
        }
        // PATCH_d76d9d98-4301-45d0-a6b6-f713ea4e916d_END

        return PlotSquaredIntegration.canPlaceBlock(player, loc) && WorldGuardIntegration.canPlaceBlock(player, loc);
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {

        // PATCH_d76d9d98-4301-45d0-a6b6-f713ea4e916d_START
        final var rokianPermsRegionEditApi = AxiomPaper.PLUGIN.getRokianPermsRegionEditApi();
        if(rokianPermsRegionEditApi != null) {
            final var result = rokianPermsRegionEditApi.canEditRegion(player, world, 
                cx * 16, cy * 16, cz * 16, cx * 16 + 15, cy * 16 + 15, cz * 16 + 15);
            
            if(result) {
                return SectionPermissionChecker.ALL_ALLOWED;
            } else {
                return SectionPermissionChecker.NONE_ALLOWED;
            }
        }
        // PATCH_d76d9d98-4301-45d0-a6b6-f713ea4e916d_END

        SectionPermissionChecker plotSquared = PlotSquaredIntegration.checkSection(player, world, cx, cy, cz);
        SectionPermissionChecker worldGuard = WorldGuardIntegration.checkSection(player, world, cx, cy, cz);

        return SectionPermissionChecker.combine(plotSquared, worldGuard);
    }

}
