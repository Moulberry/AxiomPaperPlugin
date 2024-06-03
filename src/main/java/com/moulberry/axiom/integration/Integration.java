package com.moulberry.axiom.integration;

import com.moulberry.axiom.integration.bukkit.BukkitIntegration;
import com.moulberry.axiom.integration.griefdefender.GriefDefenderIntegration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.integration.worldguard.WorldGuardIntegration;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

public class Integration {

    // todo: test if all this is working for both plotsqured, worldguard, plotsquared+worldguard

    public static boolean canBreakBlock(Player player, Block block) {
        return PlotSquaredIntegration.canBreakBlock(player, block) && WorldGuardIntegration.canBreakBlock(player, block.getLocation()) && BukkitIntegration.canBreakBlock(player, block.getLocation()) && GriefDefenderIntegration.canBreakBlock(player, block.getLocation());
    }

    public static boolean canPlaceBlock(Player player, org.bukkit.Location loc) {
        return PlotSquaredIntegration.canPlaceBlock(player, loc) && WorldGuardIntegration.canPlaceBlock(player, loc) && BukkitIntegration.canPlaceBlock(player, loc) && GriefDefenderIntegration.canPlaceBlock(player, loc);
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
        SectionPermissionChecker plotSquared = PlotSquaredIntegration.checkSection(player, world, cx, cy, cz);
        SectionPermissionChecker worldGuard = WorldGuardIntegration.checkSection(player, world, cx, cy, cz);
        SectionPermissionChecker bukkit = BukkitIntegration.checkSection(player, world, cx, cy, cz);
        SectionPermissionChecker griefDefender = GriefDefenderIntegration.checkSection(player, world, cx, cy, cz);
        List<SectionPermissionChecker> checkers = List.of(plotSquared, worldGuard, bukkit, griefDefender);

        return SectionPermissionChecker.combine(checkers);
    }

}
