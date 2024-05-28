package com.moulberry.axiom.integration.plotsquared;


import com.moulberry.axiom.integration.SectionPermissionChecker;
import com.sk89q.worldedit.regions.CuboidRegion;
import net.minecraft.core.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Set;


public class PlotSquaredIntegration {

    public record PlotBox(BlockPos min, BlockPos max) {}
    public record PlotBounds(Set<PlotBox> boxes, String worldName) {}

    public static boolean canBreakBlock(Player player, Block block) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlotSquared")) {
            return true;
        }
        return PlotSquaredIntegrationImpl.canBreakBlock(player, block);
    }

    public static boolean canPlaceBlock(Player player, org.bukkit.Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlotSquared")) {
            return true;
        }
        return PlotSquaredIntegrationImpl.canPlaceBlock(player, loc);
    }

    public static boolean isPlotWorld(World world) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlotSquared")) {
            return false;
        }
        return PlotSquaredIntegrationImpl.isPlotWorld(world);
    }

    public static PlotSquaredIntegration.PlotBounds getCurrentEditablePlot(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlotSquared")) {
            return null;
        }
        return PlotSquaredIntegrationImpl.getCurrentEditablePlot(player);
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int sectionX, int sectionY, int sectionZ) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlotSquared")) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }
        return PlotSquaredIntegrationImpl.checkSection(player, world, sectionX, sectionY, sectionZ);
    }

}
