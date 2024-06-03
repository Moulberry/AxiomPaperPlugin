package com.moulberry.axiom.integration.bukkit;

import com.moulberry.axiom.integration.SectionPermissionChecker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class BukkitIntegration {
    public static boolean canBreakBlock(Player player, Location loc) {
        return CanBuildChecker.canBreak(loc.getBlock(), player.getUniqueId());
    }

    public static boolean canPlaceBlock(Player player, Location loc) {

        return CanBuildChecker.canBuild(loc.getBlock(), player.getUniqueId());
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {

        return BukkitIntegrationImpl.checkSection(player, world, cx, cy, cz);
    }
}
