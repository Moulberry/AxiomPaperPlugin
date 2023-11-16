package com.moulberry.axiom.integration.plotsquared;

import com.moulberry.axiom.integration.Box;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import com.plotsquared.bukkit.player.BukkitPlayer;
import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.permissions.Permission;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.PlotId;
import com.plotsquared.core.plot.flag.implementations.BreakFlag;
import com.plotsquared.core.plot.flag.implementations.DoneFlag;
import com.plotsquared.core.plot.flag.types.BlockTypeWrapper;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.*;

/*
 * PlotSquared, a land and world management plugin for Minecraft.
 * Copyright (C) IntellectualSites <https://intellectualsites.com>
 * Copyright (C) IntellectualSites team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
public class PlotSquaredIntegrationImpl {

    static boolean canBreakBlock(Player player, Block block) {
        Location location = BukkitUtil.adapt(block.getLocation());
        PlotArea area = location.getPlotArea();
        if (area == null) {
            return true;
        }

        Plot plot = area.getPlot(location);
        if (plot != null) {
            BukkitPlayer plotPlayer = BukkitUtil.adapt(player);
            // == rather than <= as we only care about the "ground level" not being destroyed
            if (block.getY() == area.getMinGenHeight()) {
                if (!plotPlayer.hasPermission(Permission.PERMISSION_ADMIN_DESTROY_GROUNDLEVEL, true)) {
                    return false;
                }
            }
            if (area.notifyIfOutsideBuildArea(plotPlayer, location.getY())) {
                return false;
            }
            // check unowned plots
            if (!plot.hasOwner()) {
                return plotPlayer.hasPermission(Permission.PERMISSION_ADMIN_DESTROY_UNOWNED, true);
            }
            // player is breaking another player's plot
            if (!plot.isAdded(plotPlayer.getUUID())) {
                List<BlockTypeWrapper> destroy = plot.getFlag(BreakFlag.class);
                final BlockType blockType = BukkitAdapter.asBlockType(block.getType());
                for (final BlockTypeWrapper blockTypeWrapper : destroy) {
                    if (blockTypeWrapper.accepts(blockType)) {
                        return true;
                    }
                }
                return plotPlayer.hasPermission(Permission.PERMISSION_ADMIN_DESTROY_OTHER, true);
            }
            // plot is 'done'
            if (Settings.Done.RESTRICT_BUILDING && DoneFlag.isDone(plot)) {
                return plotPlayer.hasPermission(Permission.PERMISSION_ADMIN_BUILD_OTHER, true);
            }
            return true;
        }

        BukkitPlayer pp = BukkitUtil.adapt(player);
        return pp.hasPermission(Permission.PERMISSION_ADMIN_DESTROY_ROAD, true);
    }

    static boolean canPlaceBlock(Player player, org.bukkit.Location loc) {
        Location location = BukkitUtil.adapt(loc);
        PlotArea area = location.getPlotArea();
        if (area == null) {
            return true;
        }

        BukkitPlayer pp = BukkitUtil.adapt(player);
        Plot plot = area.getPlot(location);
        if (plot != null) {
            if (area.notifyIfOutsideBuildArea(pp, location.getY())) {
                return false;
            }
            // check unowned plots
            if (!plot.hasOwner()) {
                return pp.hasPermission(Permission.PERMISSION_ADMIN_BUILD_UNOWNED, true);
            }
            // player is breaking another player's plot
            if (!plot.isAdded(pp.getUUID())) {
                return pp.hasPermission(Permission.PERMISSION_ADMIN_BUILD_OTHER, true);
            }
            // plot is 'done'
            if (Settings.Done.RESTRICT_BUILDING && DoneFlag.isDone(plot)) {
                return pp.hasPermission(Permission.PERMISSION_ADMIN_BUILD_OTHER, true);
            }
            return true;
        }

        return pp.hasPermission(Permission.PERMISSION_ADMIN_BUILD_ROAD, true);
    }

    private static final WeakHashMap<World, Boolean> plotWorldCache = new WeakHashMap<>();

    static boolean isPlotWorld(World world) {
        if (plotWorldCache.containsKey(world)) {
            return plotWorldCache.get(world);
        }

        String worldName = world.getName();
        PlotArea[] plotAreas = PlotSquared.get().getPlotAreaManager().getPlotAreas(worldName, null);
        boolean isPlotWorld = plotAreas.length > 0;
        plotWorldCache.put(world, isPlotWorld);
        return isPlotWorld;
    }

    static SectionPermissionChecker checkSection(Player player, World world, int sectionX, int sectionY, int sectionZ) {
        int minX = sectionX * 16;
        int minY = sectionY * 16;
        int minZ = sectionZ * 16;
        int maxX = sectionX * 16 + 15;
        int maxY = sectionY * 16 + 15;
        int maxZ = sectionZ * 16 + 15;

        PlotArea[] plotAreas = PlotSquared.get().getPlotAreaManager().getPlotAreas(world.getName(), new CuboidRegion(
                BlockVector3.at(minX, minY, minZ),
                BlockVector3.at(maxX, maxY, maxZ)
        ));

        if (plotAreas.length == 0) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }

        Set<Plot> checkedPlots = new HashSet<>();
        List<Box> allowed = new ArrayList<>();

        for (PlotArea plotArea : plotAreas) {
            for (int px = minX; px <= maxX; px += 15) {
                for (int py = minY; py <= maxY; py += 15) {
                    for (int pz = minZ; pz <= maxZ; pz += 15) {
                        PlotId pid = plotArea.getPlotManager().getPlotId(px, py, pz);
                        if (pid == null) continue;
                        Plot plot = plotArea.getOwnedPlot(pid);
                        if (plot == null) continue;

                        if (!checkedPlots.add(plot)) continue;

                        if (!plot.hasOwner()) continue;
                        if (!plot.isAdded(player.getUniqueId())) continue;
                        if (Settings.Done.RESTRICT_BUILDING && DoneFlag.isDone(plot)) continue;

                        for (CuboidRegion region : plot.getRegions()) {
                            BlockVector3 minPoint = region.getMinimumPoint();
                            BlockVector3 maxPoint = region.getMaximumPoint();

                            int minPlotX = Math.max(minPoint.getX(), minX);
                            int minPlotY = Math.max(minPoint.getY(), minY);
                            int minPlotZ = Math.max(minPoint.getZ(), minZ);
                            int maxPlotX = Math.min(maxPoint.getX(), maxX);
                            int maxPlotY = Math.min(maxPoint.getY(), maxY);
                            int maxPlotZ = Math.min(maxPoint.getZ(), maxZ);

                            if (minPlotX > maxPlotX) continue;
                            if (minPlotY > maxPlotY) continue;
                            if (minPlotZ > maxPlotZ) continue;

                            if (minPlotX <= minX && minPlotY <= minY && minPlotZ <= minZ &&
                                    maxPlotX >= maxX && maxPlotY >= maxY && maxPlotZ >= maxZ) {
                                return SectionPermissionChecker.ALL_ALLOWED;
                            }

                            allowed.add(new Box(minPlotX - minX, minPlotY - minY, minPlotZ - minZ,
                                    maxPlotX - minX, maxPlotY - minY, maxPlotZ - minZ));
                        }
                    }
                }
            }
        }

        // Combine
        main:
        while (allowed.size() >= 2) {
            for (int i = 0; i < allowed.size() - 1; i++) {
                Box first = allowed.get(i);
                for (int j = i + 1; j < allowed.size(); j++) {
                    Box second = allowed.get(j);

                    Box combined = first.tryCombine(second);
                    if (combined != null) {
                        allowed.remove(j);
                        allowed.remove(i);
                        allowed.add(combined);
                        continue main;
                    }
                }
            }
            break;
        }

        return SectionPermissionChecker.fromAllowedBoxes(allowed);
    }
}