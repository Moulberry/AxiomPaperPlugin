package com.moulberry.axiom.integration.griefdefender;

import com.moulberry.axiom.integration.SectionPermissionChecker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class GriefDefenderIntegration {
    public static boolean canBreakBlock(Player player, Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("GriefDefender")) {
            return true;
        }
        return GriefDefenderIntegrationImpl.isBuilder(player, loc);
    }

    public static boolean canPlaceBlock(Player player, Location loc) {
        return canBreakBlock(player, loc);
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
        if (!Bukkit.getPluginManager().isPluginEnabled("GriefDefender")) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }
        return GriefDefenderIntegrationImpl.checkSection(player, world, cx, cy, cz);
    }
}
