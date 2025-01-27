package com.moulberry.axiom.integration;

import com.moulberry.axiom.event.AxiomBlockBreakEvent;
import com.moulberry.axiom.event.AxiomBlockPlaceEvent;
import com.moulberry.axiom.event.AxiomModifyRegionEvent;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.integration.worldguard.WorldGuardIntegration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class Integration {

    // todo: test if all this is working for both plotsqured, worldguard, plotsquared+worldguard

    public static boolean canBreakBlock(Player player, Block block) {

        // Allow other permission plugins to cancel the block break event
        final var event = new AxiomBlockBreakEvent(player, block);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled()) {
            return false;
        }

        return PlotSquaredIntegration.canBreakBlock(player, block) && WorldGuardIntegration.canBreakBlock(player, block.getLocation());
    }

    public static boolean canPlaceBlock(Player player, org.bukkit.Location loc) {

        // Allow other permission plugins to cancel the block place event
        final var event = new AxiomBlockPlaceEvent(player, loc);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled()) {
            return false;
        }

        return PlotSquaredIntegration.canPlaceBlock(player, loc) && WorldGuardIntegration.canPlaceBlock(player, loc);
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {

        // Allow other permission plugins to cancel the modify region event
        final var event = new AxiomModifyRegionEvent(player, world, 
            cx * 16, cy * 16, cz * 16, 
            cx * 16 + 15, cy * 16 + 15, cz * 16 + 15);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled()) {
            return SectionPermissionChecker.NONE_ALLOWED;
        }

        SectionPermissionChecker plotSquared = PlotSquaredIntegration.checkSection(player, world, cx, cy, cz);
        SectionPermissionChecker worldGuard = WorldGuardIntegration.checkSection(player, world, cx, cy, cz);

        return SectionPermissionChecker.combine(plotSquared, worldGuard);
    }

}
