package com.moulberry.axiom.integration.griefdefender;

import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.TrustTypes;
import com.griefdefender.lib.flowpowered.math.vector.Vector3i;
import com.moulberry.axiom.integration.Box;
import com.moulberry.axiom.integration.BoxWithBoolean;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

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

        int minX = cx * 16;
        int minY = cy * 16;
        int minZ = cz * 16;
        int maxX = cx * 16 + 15;
        int maxY = cy * 16 + 15;
        int maxZ = cz * 16 + 15;

        List<BoxWithBoolean> finalRegions = new ArrayList<>();
        List<Box> allowRegions = new ArrayList<>();
        List<Box> denyRegions = new ArrayList<>();

        // 獲取所有相關的領地
        List<Claim> claims = new ArrayList<>(GriefDefender.getCore().getAllClaims());
        claims.removeIf(claim -> !claim.getWorldUniqueId().equals(world.getUID()));


        for (Claim claim : claims) {
            Vector3i lesser = claim.getLesserBoundaryCorner();
            Vector3i greater = claim.getGreaterBoundaryCorner();

            // 計算與當前區塊的交集
            int claimMinX = Math.max(lesser.getX(), minX) - minX;
            int claimMinY = Math.max(lesser.getY(), minY) - minY;
            int claimMinZ = Math.max(lesser.getZ(), minZ) - minZ;
            int claimMaxX = Math.min(greater.getX(), maxX) - minX;
            int claimMaxY = Math.min(greater.getY(), maxY) - minY;
            int claimMaxZ = Math.min(greater.getZ(), maxZ) - minZ;

            // 檢查是否有交集
            if (claimMaxX >= claimMinX && claimMaxY >= claimMinY && claimMaxZ >= claimMinZ) {
                Box box = new Box(claimMinX, claimMinY, claimMinZ, claimMaxX, claimMaxY, claimMaxZ);

                if (claim.isUserTrusted(player.getUniqueId(), TrustTypes.BUILDER)) {
                    allowRegions.add(box);
                } else {
                    denyRegions.add(box);
                }
            }
        }

        // 處理區域
        if (!denyRegions.isEmpty()) {
            Box.combineAll(denyRegions);
            for (Box denyRegion : denyRegions) {
                finalRegions.add(new BoxWithBoolean(denyRegion, false));
            }
        }

        if (!allowRegions.isEmpty()) {
            Box.combineAll(allowRegions);
            for (Box allowRegion : allowRegions) {
                finalRegions.add(new BoxWithBoolean(allowRegion, true));
            }
        }

        // 如果沒有任何領地覆蓋，使用默認權限
        if (finalRegions.isEmpty()) {
            // GriefDefender 默認情況下不允許建造
            return SectionPermissionChecker.NONE_ALLOWED;
        }

        return SectionPermissionChecker.fromBoxWithBooleans(finalRegions, false);
    }
}