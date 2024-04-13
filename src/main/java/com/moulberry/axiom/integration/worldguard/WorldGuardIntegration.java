package com.moulberry.axiom.integration.worldguard;

import com.moulberry.axiom.integration.SectionPermissionChecker;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class WorldGuardIntegration {

    public static boolean canBreakBlock(Player player, org.bukkit.Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return true;
        }
        return WorldGuardIntegrationImpl.canBreakBlock(player, loc);
    }

    public static boolean canPlaceBlock(Player player, org.bukkit.Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return true;
        }
        return WorldGuardIntegrationImpl.canPlaceBlock(player, loc);
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }
        return WorldGuardIntegrationImpl.checkSection(player, world, cx, cy, cz);
    }

}
