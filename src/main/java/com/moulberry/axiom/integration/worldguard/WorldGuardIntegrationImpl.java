package com.moulberry.axiom.integration.worldguard;

import com.google.common.collect.Iterables;
import com.moulberry.axiom.integration.Box;
import com.moulberry.axiom.integration.BoxWithBoolean;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.FlagValueCalculator;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.*;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WorldGuardIntegrationImpl {

    static boolean canBreakBlock(Player player, org.bukkit.Location loc) {
        return testBuild(player, loc, Flags.BLOCK_BREAK);
    }

    static boolean canPlaceBlock(Player player, org.bukkit.Location loc) {
        return testBuild(player, loc, Flags.BLOCK_PLACE);
    }

    private static boolean testBuild(Player player, org.bukkit.Location loc, StateFlag flag) {
        WorldGuardPlatform platform = WorldGuard.getInstance().getPlatform();

        com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(loc.getWorld());
        LocalPlayer worldGuardPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

        if (platform.getSessionManager().hasBypass(worldGuardPlayer, worldEditWorld)) {
            return true;
        }

        RegionContainer regionContainer = platform.getRegionContainer();
        if (regionContainer == null) {
            return true;
        }

        RegionQuery query = regionContainer.createQuery();
        return query.testBuild(BukkitAdapter.adapt(loc), worldGuardPlayer, flag);
    }

    static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
        int minX = cx * 16;
        int minY = cy * 16;
        int minZ = cz * 16;
        int maxX = cx * 16 + 15;
        int maxY = cy * 16 + 15;
        int maxZ = cz * 16 + 15;

        WorldGuardPlatform platform = WorldGuard.getInstance().getPlatform();

        RegionContainer regionContainer = platform.getRegionContainer();
        if (regionContainer == null) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }

        com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(world);
        LocalPlayer worldGuardPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

        // Don't do any protection if player has bypass
        if (platform.getSessionManager().hasBypass(worldGuardPlayer, worldEditWorld)) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }

        RegionManager regionManager = regionContainer.get(worldEditWorld);
        if (regionManager == null) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }

        BlockVector3 min = BlockVector3.at(minX, minY, minZ);
        BlockVector3 max = BlockVector3.at(maxX, maxY, maxZ);
        ProtectedRegion test = new ProtectedCuboidRegion("dummy", min, max);
        Iterable<ProtectedRegion> regions = regionManager.getApplicableRegions(test, RegionQuery.QueryOption.COMPUTE_PARENTS);
        ProtectedRegion globalRegion = regionManager.getRegion("__global__");
        regions = globalRegion == null ? regions : Iterables.concat(regions, Collections.singletonList(globalRegion));

        Box sectionBox = new Box(0, 0, 0, 15, 15, 15);
        int lastPriority = Integer.MIN_VALUE;

        List<Box> denyRegions = new ArrayList<>();
        List<Box> allowRegions = new ArrayList<>();

        List<BoxWithBoolean> finalRegions = new ArrayList<>();

        for (ProtectedRegion region : regions) {
            int priority = FlagValueCalculator.getPriorityOf(region);

            if (priority != lastPriority) {
                lastPriority = priority;

                if (addRegions(sectionBox, denyRegions, allowRegions, finalRegions)) {
                    break;
                }
            }

            StateFlag.State value = FlagValueCalculator.getEffectiveFlagOf(region, Flags.BUILD, worldGuardPlayer);
            if (value != null) {
                if (region instanceof GlobalProtectedRegion) {
                    if (value == StateFlag.State.DENY) {
                        if (finalRegions.isEmpty()) {
                            return SectionPermissionChecker.NONE_ALLOWED;
                        } else {
                            denyRegions.add(sectionBox);
                        }
                    } else if (value == StateFlag.State.ALLOW) {
                        allowRegions.add(sectionBox);
                    }
                } else if (region instanceof ProtectedCuboidRegion || value == StateFlag.State.DENY) {
                    // Note: we do this for DENY, even if the
                    // type isn't a cuboid region simply to
                    // be cautious

                    BlockVector3 regionMin = region.getMinimumPoint();
                    BlockVector3 regionMax = region.getMaximumPoint();

                    int regionMinX = Math.max(regionMin.getBlockX(), minX) - minX;
                    int regionMinY = Math.max(regionMin.getBlockY(), minY) - minY;
                    int regionMinZ = Math.max(regionMin.getBlockZ(), minZ) - minZ;
                    int regionMaxX = Math.min(regionMax.getBlockX(), maxX) - minX;
                    int regionMaxY = Math.min(regionMax.getBlockY(), maxY) - minY;
                    int regionMaxZ = Math.min(regionMax.getBlockZ(), maxZ) - minZ;

                    Box box = new Box(regionMinX, regionMinY, regionMinZ, regionMaxX, regionMaxY, regionMaxZ);
                    if (value == StateFlag.State.DENY) {
                        denyRegions.add(box);
                    } else {
                        allowRegions.add(box);
                    }
                }
                continue;
            }

            // todo: handle membership

            // The BUILD flag is implicitly set on every region where
            // PASSTHROUGH is not set to ALLOW
            // todo: handle passthrough
//            if (FlagValueCalculator.getEffectiveFlagOf(region, Flags.PASSTHROUGH, worldGuardPlayer) != StateFlag.State.ALLOW) {
//            }
        }

        addRegions(sectionBox, denyRegions, allowRegions, finalRegions);

        return SectionPermissionChecker.fromBoxWithBooleans(finalRegions, Flags.BUILD.getDefault() == StateFlag.State.ALLOW);
    }

    private static boolean addRegions(Box sectionBox, List<Box> denyRegions, List<Box> allowRegions, List<BoxWithBoolean> finalRegions) {
        List<Box> denyRegionsCopy = new ArrayList<>(denyRegions);
        denyRegions.clear();

        List<Box> allowRegionsCopy = new ArrayList<>(allowRegions);
        allowRegions.clear();

        if (!denyRegionsCopy.isEmpty()) {
            Box.combineAll(denyRegionsCopy);

            for (Box denyRegion : denyRegionsCopy) {
                finalRegions.add(new BoxWithBoolean(denyRegion, false));

                if (denyRegion.completelyOverlaps(sectionBox)) {
                    return true;
                }
            }
        }
        if (!allowRegionsCopy.isEmpty()) {
            Box.combineAll(allowRegionsCopy);

            for (Box allowRegion : allowRegionsCopy) {
                finalRegions.add(new BoxWithBoolean(allowRegion, true));

                if (allowRegion.completelyOverlaps(sectionBox)) {
                    return true;
                }
            }
        }

        return false;
    }

}
