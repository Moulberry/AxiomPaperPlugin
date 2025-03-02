package com.moulberry.axiom.integration;

import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.integration.worldguard.WorldGuardIntegration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Integration {

    public interface CustomIntegration {
        boolean canBreakBlock(Player player, Block block);
        boolean canPlaceBlock(Player player, org.bukkit.Location loc);
        SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz);
    }

    private static final CustomIntegration NO_OP_CUSTOM_INTEGRATION = new CustomIntegration() {
        @Override
        public boolean canBreakBlock(Player player, Block block) {
            return true;
        }

        @Override
        public boolean canPlaceBlock(Player player, Location loc) {
            return true;
        }

        @Override
        public SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }
    };

    private static CustomIntegration customIntegration = NO_OP_CUSTOM_INTEGRATION;
    private static final Map<String, CustomIntegration> customIntegrationsByPlugin = new LinkedHashMap<>();

    public static void registerCustomIntegration(Plugin plugin, CustomIntegration custom) {
        customIntegrationsByPlugin.put(plugin.getName(), custom);

        customIntegration = NO_OP_CUSTOM_INTEGRATION;

        for (CustomIntegration integration : customIntegrationsByPlugin.values()) {
            if (customIntegration == NO_OP_CUSTOM_INTEGRATION) {
                customIntegration = integration;
            } else {
                CustomIntegration nextIntegrationInChain = customIntegration;
                customIntegration = new CustomIntegration() {
                    @Override
                    public boolean canBreakBlock(Player player, Block block) {
                        return integration.canBreakBlock(player, block) && nextIntegrationInChain.canBreakBlock(player, block);
                    }

                    @Override
                    public boolean canPlaceBlock(Player player, Location loc) {
                        return integration.canPlaceBlock(player, loc) && nextIntegrationInChain.canPlaceBlock(player, loc);
                    }

                    @Override
                    public SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
                        SectionPermissionChecker one = integration.checkSection(player, world, cx, cy, cz);
                        SectionPermissionChecker two = nextIntegrationInChain.checkSection(player, world, cx, cy, cz);

                        return SectionPermissionChecker.combine(one, two);
                    }
                };
            }
        }
    }

    public static boolean canBreakBlock(Player player, Block block) {
        return PlotSquaredIntegration.canBreakBlock(player, block) && WorldGuardIntegration.canBreakBlock(player, block.getLocation()) && customIntegration.canBreakBlock(player, block);
    }

    public static boolean canPlaceBlock(Player player, org.bukkit.Location loc) {
        return PlotSquaredIntegration.canPlaceBlock(player, loc) && WorldGuardIntegration.canPlaceBlock(player, loc) && customIntegration.canPlaceBlock(player, loc);
    }

    public static SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz) {
        SectionPermissionChecker plotSquared = PlotSquaredIntegration.checkSection(player, world, cx, cy, cz);
        if (plotSquared.noneAllowed()) {
            return SectionPermissionChecker.NONE_ALLOWED;
        }

        SectionPermissionChecker worldGuard = WorldGuardIntegration.checkSection(player, world, cx, cy, cz);
        if (worldGuard.noneAllowed()) {
            return SectionPermissionChecker.NONE_ALLOWED;
        }

        SectionPermissionChecker custom = customIntegration.checkSection(player, world, cx, cy, cz);
        if (custom.noneAllowed()) {
            return SectionPermissionChecker.NONE_ALLOWED;
        }

        return SectionPermissionChecker.combine(SectionPermissionChecker.combine(plotSquared, worldGuard), custom);
    }

}
