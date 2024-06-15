package com.moulberry.axiom.integration.griefdefender;

import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.TrustTypes;
import com.griefdefender.lib.flowpowered.math.vector.Vector3i;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GriefDefenderIntegrationImpl {

    public static boolean isBuilder(Player player, Location loc) {
        if (!GriefDefender.getCore().isEnabled(loc.getWorld().getUID())) {
            return true;
        }

        Claim claim = GriefDefender.getCore().getClaimAt(loc);

        if (claim == null) {
            return true;
        }
        return claim.isUserTrusted(player.getUniqueId(), TrustTypes.BUILDER);
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
        if (!GriefDefender.getCore().isEnabled(world.getUID())) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }

        int minX = Math.min(cx * 16, cx * 16 + 15);
        int minY = Math.min(cy * 16, cy * 16 + 15);
        int minZ = Math.min(cz * 16, cz * 16 + 15);
        int maxX = Math.max(cx * 16, cx * 16 + 15);
        int maxY = Math.max(cy * 16, cy * 16 + 15);
        int maxZ = Math.max(cz * 16, cz * 16 + 15);


        Vector3i lesserBoundaryCorner = new Vector3i(minX, minY, minZ);
        Vector3i greaterBoundaryCorner = new Vector3i(maxX, maxY, maxZ);

        List<Claim> claims = new ArrayList<>(GriefDefender.getCore().getAllClaims());
        if (!GriefDefender.getCore().isEnabled(world.getUID())) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }

        claims.removeIf(claim -> !claim.getWorldUniqueId().equals(world.getUID()));

        claims.removeIf(claim -> !claim.isUserTrusted(player.getUniqueId(), TrustTypes.BUILDER));

        List<Claim> claimList = getClaimInArea(claims, lesserBoundaryCorner, greaterBoundaryCorner);

        if (claimList.isEmpty()) {

            return SectionPermissionChecker.NONE_ALLOWED;
        }

        return SectionPermissionChecker.ALL_ALLOWED;
    }

    private static @NotNull List<Claim> getClaimInArea(List<Claim> claims, Vector3i lesserBoundaryCorner, Vector3i greaterBoundaryCorner) {

        List<Claim> claimList = new ArrayList<>();
        for (Claim claim : claims) {
            Vector3i lesser = claim.getLesserBoundaryCorner();
            Vector3i greater = claim.getGreaterBoundaryCorner();
            if (lesser.getX() <= lesserBoundaryCorner.getX() && lesser.getZ() <= lesserBoundaryCorner.getZ() && greater.getX() >= greaterBoundaryCorner.getX() && greater.getZ() >= greaterBoundaryCorner.getZ()) {

                claimList.add(claim);
            }
        }

        return claimList;
    }
}
