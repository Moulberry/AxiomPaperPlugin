package com.moulberry.axiom.integration.bukkit;

import com.moulberry.axiom.integration.SectionPermissionChecker;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class BukkitIntegrationImpl {
    static SectionPermissionChecker checkSection(Player player, World world, int sectionX, int sectionY, int sectionZ) {
        int minX = sectionX * 16;
        int minY = sectionY * 16;
        int minZ = sectionZ * 16;
        int maxX = sectionX * 16 + 15;
        int maxY = sectionY * 16 + 15;
        int maxZ = sectionZ * 16 + 15;

        boolean allowed = true;

        for (int x = minX; x <= maxX; x += 4) {
            for (int y = minY; y <= maxY; y += 4) {
                for (int z = minZ; z <= maxZ; z += 4) {
                    if (!CanBuildChecker.canBuild(player, world, x, y, z)) {
                        allowed = false;
                    }
                }
            }
        }

        if (allowed) {
            return SectionPermissionChecker.ALL_ALLOWED;
        } else {
            return SectionPermissionChecker.NONE_ALLOWED;
        }
    }
}
