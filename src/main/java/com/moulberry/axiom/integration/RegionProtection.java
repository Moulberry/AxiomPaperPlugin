package com.moulberry.axiom.integration;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class RegionProtection {

    private final RegionProtectionWorldGuard worldGuard;

    public RegionProtection(Player player, World world) {
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            this.worldGuard = RegionProtectionWorldGuard.tryCreate(player, world);
        } else {
            this.worldGuard = null;
        }
    }

    public boolean canBuildInSection(int cx, int cy, int cz) {
        if (this.worldGuard != null && !this.worldGuard.canBuildInSection(cx, cy, cz)) return false;
        // todo: PlotSquared
        return true;
    }



}
