package com.moulberry.axiom.integration.plotsquared;


import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;


public class PlotSquaredIntegration {

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
            return true;
        }
        return PlotSquaredIntegrationImpl.isPlotWorld(world);
    }

}
