package com.moulberry.axiom.integration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.FlagValueCalculator;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.*;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

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
        if (regionContainer == null) return null;

        com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(world);
        LocalPlayer worldGuardPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

        // Don't do any protection if player has bypass
        if (platform.getSessionManager().hasBypass(worldGuardPlayer, worldEditWorld)) {
            // todo: enable bypass
//            return null;
        }

        RegionManager regionManager = regionContainer.get(worldEditWorld);
        if (regionManager == null) return null;

        return new RegionProtectionWorldGuard(worldGuardPlayer, regionManager);
    }

    public SectionProtection getSection(int cx, int cy, int cz) {
        BlockVector3 min = BlockVector3.at(cx*16, cy*16, cz*16);
        BlockVector3 max = BlockVector3.at(cx*16+15, cy*16+15, cz*16+15);
        ProtectedRegion test = new ProtectedCuboidRegion("dummy", min, max);
        ApplicableRegionSet regions = this.regionManager.getApplicableRegions(test, RegionQuery.QueryOption.COMPUTE_PARENTS);

        int minimumPriority = Integer.MIN_VALUE;

        Map<ProtectedRegion, StateFlag.State> consideredValues = new HashMap<>();
        Set<ProtectedRegion> ignoredParents = new HashSet<>();

        for (ProtectedRegion region : regions) {
            int priority = FlagValueCalculator.getPriorityOf(region);

            // todo: this logic doesn't work for us in determining ALLOW, DENY, CHECK
            if (priority < minimumPriority) {
                break;
            }

            // todo: have to keep track of 2 booleans: partialAllow & partialDeny

            if (ignoredParents.contains(region)) {
                continue;
            }

            StateFlag.State value = FlagValueCalculator.getEffectiveFlagOf(region, Flags.BUILD, this.player);
            if (value != null) {
                minimumPriority = priority;
                consideredValues.put(region, value);
            }

            addParents(ignoredParents, region);

            // The BUILD flag is implicitly set on every region where
            // PASSTHROUGH is not set to ALLOW
            if (minimumPriority != priority && Flags.BUILD.implicitlySetWithMembership() &&
                    FlagValueCalculator.getEffectiveFlagOf(region, Flags.PASSTHROUGH, this.player) != StateFlag.State.ALLOW) {
                minimumPriority = priority;
            }
        }

        if (consideredValues.isEmpty()) {
            if (Flags.BUILD.usesMembershipAsDefault()) {
                // todo
//                switch (getMembership(subject)) {
//                    case FAIL:
//                        return ImmutableList.of();
//                    case SUCCESS:
//                        return (Collection<V>) ImmutableList.of(StateFlag.State.ALLOW);
//                }
            }

            System.out.println("returning default");
            StateFlag.State fallback = Flags.BUILD.getDefault();
            return fallback == StateFlag.State.DENY ? SectionProtection.DENY : SectionProtection.ALLOW;
        }

        boolean hasPartialDeny = false;
        for (Map.Entry<ProtectedRegion, StateFlag.State> entry : consideredValues.entrySet()) {
            ProtectedRegion region = entry.getKey();
            if (entry.getValue() == StateFlag.State.DENY) {
                System.out.println("found region with deny!");
                if (region instanceof GlobalProtectedRegion) {
                    return SectionProtection.DENY;
                } else if (region instanceof ProtectedCuboidRegion && doesRegionCompletelyContainSection(region, cx, cy, cz)) {
                    return SectionProtection.DENY;
                }
                hasPartialDeny = true;
            }
        }

        if (hasPartialDeny) {
            System.out.println("returning check!");
            return new SectionProtection() {
                @Override
                public SectionState getSectionState() {
                    return SectionState.CHECK;
                }

                @Override
                public boolean check(int wx, int wy, int wz) {
                    return true;
                }
            };
            // return complex thing
        }

        System.out.println("returning allow!");
        return SectionProtection.ALLOW;
    }

    private boolean doesRegionCompletelyContainSection(ProtectedRegion region, int cx, int cy, int cz) {
        BlockVector3 regionMin = region.getMinimumPoint();

        if (regionMin.getBlockX() > cx*16) return false;
        if (regionMin.getBlockY() > cy*16) return false;
        if (regionMin.getBlockZ() > cz*16) return false;

        BlockVector3 regionMax = region.getMaximumPoint();

        if (regionMax.getBlockX() < cx*16+15) return false;
        if (regionMax.getBlockY() < cy*16+15) return false;
        if (regionMax.getBlockZ() < cz*16+15) return false;

        return true;
    }

    private void addParents(Set<ProtectedRegion> ignored, ProtectedRegion region) {
        ProtectedRegion parent = region.getParent();

        while (parent != null) {
            ignored.add(parent);
            parent = parent.getParent();
        }
    }

    public boolean canBuild(int x, int y, int z) {
        return this.regionManager.getApplicableRegions(BlockVector3.at(x, y, z)).testState(this.player, Flags.BUILD);
    }

    public boolean isAllowed(LocalPlayer player, ProtectedRegion protectedRegion) {
        return protectedRegion.isOwner(player) || protectedRegion.isMember(player);
    }

}
