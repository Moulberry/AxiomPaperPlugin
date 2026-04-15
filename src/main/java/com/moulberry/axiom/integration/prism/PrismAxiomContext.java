package com.moulberry.axiom.integration.prism;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.prism_mc.prism.api.activities.Activity;
import org.prism_mc.prism.api.containers.PlayerContainer;
import org.prism_mc.prism.api.services.modifications.ModificationQueueMode;
import org.prism_mc.prism.api.services.modifications.ModificationResult;

import net.minecraft.server.level.ServerLevel;

final class PrismAxiomContext {
    private PrismAxiomContext() {
    }

    static ModificationResult defaultResult(Activity activity, ModificationQueueMode mode) {
        return ModificationResult.builder().activity(activity).statusFromMode(mode).build();
    }

    static ModificationResult skippedResult(Activity activity) {
        return ModificationResult.builder().activity(activity).skipped().build();
    }

    static ModificationResult erroredResult(Activity activity) {
        return ModificationResult.builder().activity(activity).errored().build();
    }

    @Nullable
    static Player onlinePlayer(PlayerContainer playerContainer) {
        return playerContainer.uuid() == null ? null : Bukkit.getPlayer(playerContainer.uuid());
    }

    @Nullable
    static World world(Activity activity) {
        return Bukkit.getWorld(activity.worldUuid());
    }

    @Nullable
    static ServerLevel serverLevel(Activity activity) {
        World world = world(activity);
        return world instanceof CraftWorld craftWorld ? craftWorld.getHandle() : null;
    }
}
