package com.moulberry.axiom.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class RegionProtectionWorldGuard {

    private final LocalPlayer player;
    private final RegionManager regionManager;

    public RegionProtectionWorldGuard(LocalPlayer player, RegionManager regionManager) {
        this.player = player;
        this.regionManager = regionManager;
    }

    @Nullable
    public static RegionProtectionWorldGuard tryCreate(Player player, World world) {
        WorldGuardPlatform platform = WorldGuard.getInstance().getPlatform();

        RegionContainer regionContainer = platform.getRegionContainer();
        com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(world);
        LocalPlayer worldGuardPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

        // Don't do any protection if player has bypass
        if (platform.getSessionManager().hasBypass(worldGuardPlayer, worldEditWorld)) {
            return null;
        }

        RegionManager regionManager = regionContainer.get(worldEditWorld);
        if (regionManager == null) return null;

        return new RegionProtectionWorldGuard(worldGuardPlayer, regionManager);
    }

    public boolean canBuildInSection(int cx, int cy, int cz) {
        BlockVector3 min = BlockVector3.at(cx*16, cy*16, cz*16);
        BlockVector3 max = BlockVector3.at(cx*16+15, cy*16+15, cz*16+15);
        ProtectedRegion test = new ProtectedCuboidRegion("dummy", min, max);
        ApplicableRegionSet regions = this.regionManager.getApplicableRegions(test, RegionQuery.QueryOption.COMPUTE_PARENTS);
        return regions.testState(this.player, Flags.BUILD);
    }

}
