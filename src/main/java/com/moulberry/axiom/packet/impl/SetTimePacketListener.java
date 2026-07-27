package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.event.AxiomTimeChangeEvent;
import com.moulberry.axiom.integration.prism.PrismIntegration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

public class SetTimePacketListener implements PacketHandler {

    private final AxiomPaper plugin;
    public SetTimePacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(player, AxiomPermission.WORLD_TIME)) {
            return;
        }

        ResourceKey<Level> key = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
        Integer time = friendlyByteBuf.readNullable(FriendlyByteBuf::readInt);
        Boolean freezeTime = friendlyByteBuf.readNullable(FriendlyByteBuf::readBoolean);

        if (time == null && freezeTime == null) return;

        World world = player.getWorld();
        ServerLevel level = ((CraftWorld) world).getHandle();
        if (!level.dimension().equals(key)) return;

        // Don't allow on plot worlds
        if (PlotSquaredIntegration.isPlotWorld(world)) {
            return;
        }

        // Call modify world
        if (!this.plugin.canModifyWorld(player, world)) {
            return;
        }

        // Call time change event
        AxiomTimeChangeEvent timeChangeEvent = new AxiomTimeChangeEvent(player, time, freezeTime);
        Bukkit.getPluginManager().callEvent(timeChangeEvent);
        if (timeChangeEvent.isCancelled()) return;

        long oldTime = world.getTime();
        boolean oldAdvanceTime = Boolean.TRUE.equals(world.getGameRuleValue(org.bukkit.GameRules.ADVANCE_TIME));

        // Change time
        if (time != null) world.setTime(time);
        if (freezeTime != null) level.getGameRules().set(GameRules.ADVANCE_TIME, !freezeTime, null);

        PrismIntegration.logWorldTimeChange(player, world, oldTime, oldAdvanceTime,
            world.getTime(), Boolean.TRUE.equals(world.getGameRuleValue(org.bukkit.GameRules.ADVANCE_TIME)));
    }

}
