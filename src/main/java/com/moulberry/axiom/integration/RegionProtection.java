package com.moulberry.axiom.integration;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RegionProtection {

    private final RegionProtectionWorldGuard worldGuard;

    public RegionProtection(Player player, World world) {
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            this.worldGuard = RegionProtectionWorldGuard.tryCreate(player, world);
        } else {
            this.worldGuard = null;
        }
    }

    public SectionProtection getSection(int cx, int cy, int cz) {
        List<SectionProtection> protections = new ArrayList<>();
        if (this.worldGuard != null) {
            return this.worldGuard.getSection(cx, cy, cz);
        }
        // todo: PlotSquared
        return SectionProtection.ALLOW;
    }

    public boolean canBuild(int x, int y, int z) {
        if (this.worldGuard != null && !this.worldGuard.canBuild(x, y, z)) return false;
        // todo: PlotSquared
        return true;
    }



}
