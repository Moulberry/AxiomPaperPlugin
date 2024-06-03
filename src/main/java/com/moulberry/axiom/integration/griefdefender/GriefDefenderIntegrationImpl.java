package com.moulberry.axiom.integration.griefdefender;

import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.User;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.lib.flowpowered.math.vector.Vector3i;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GriefDefenderIntegrationImpl {
    public static boolean canBreakBlock(Player player, Location loc) {
        if(!GriefDefender.getCore().isEnabled(loc.getWorld().getUID())) {
            return true;
        }
        final User user = GriefDefender.getCore().getUser(player.getUniqueId());

        if (user == null) {
            return false;
        }

        return user.canBreak(loc);
    }

    public static boolean canPlaceBlock(Player player, Location loc) {
        if(!GriefDefender.getCore().isEnabled(loc.getWorld().getUID())) {
            return true;
        }
        final User user = GriefDefender.getCore().getUser(player.getUniqueId());

        if (user == null) {
            return false;
        }

        ItemStack itemStack = new ItemStack(loc.getBlock().getType());

        return user.canPlace(itemStack, loc);
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
        if(!GriefDefender.getCore().isEnabled(world.getUID())) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }

        int minX = cx * 16;
        int minY = cy * 16;
        int minZ = cz * 16;
        int maxX = cx * 16 + 15;
        int maxY = cy * 16 + 15;
        int maxZ = cz * 16 + 15;

        Vector3i lesserBoundaryCorner = new Vector3i(minX, minY, minZ);
        Vector3i greaterBoundaryCorner = new Vector3i(maxX, maxY, maxZ);

        List<Claim> claimList =  GriefDefender.getCore().getAllClaims();

        claimList.removeIf(claim -> !claim.getWorldUniqueId().equals(world.getUID()));

        claimList.removeIf(claim -> claim.getLesserBoundaryCorner().getX() < lesserBoundaryCorner.getX() && claim.getLesserBoundaryCorner().getY() < lesserBoundaryCorner.getY() && claim.getLesserBoundaryCorner().getZ() < lesserBoundaryCorner.getZ());
        claimList.removeIf(claim -> claim.getGreaterBoundaryCorner().getX() > greaterBoundaryCorner.getX() && claim.getGreaterBoundaryCorner().getY() > greaterBoundaryCorner.getY() && claim.getGreaterBoundaryCorner().getZ() > greaterBoundaryCorner.getZ());

        if(claimList.isEmpty()) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }

        final User user = GriefDefender.getCore().getUser(player.getUniqueId());

        for (Claim claim : claimList) {
            if (!claim.canBreak(player, new Location(world, cx, cy, cz), user)) {
                return SectionPermissionChecker.NONE_ALLOWED;
            }
        }

        return SectionPermissionChecker.ALL_ALLOWED;
    }
}
