package com.moulberry.axiom.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.integration.worldguard.WorldGuardIntegration;
import com.moulberry.axiom.restrictions.Restrictions;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Predicate;

public class AxiomDebugCommand {

    /**
     * Command requires either the axiom.debug permission or for you to have the UUID d0e05de7-6067-454d-beae-c6d19d886191
     * The command isn't capable of modifying the world, only checking various properties for debugging purposes
     * It should be 100% safe to give to any player, but is locked behind some restrictions due to the potential
     * for lagging the server by spamming certain commands
     */

    private static final UUID MOULBERRY_UUID = UUID.fromString("d0e05de7-6067-454d-beae-c6d19d886191");

    public static void register(AxiomPaper axiomPaper, Commands manager) {
        Predicate<CommandSourceStack> predicate = commandSourceStack -> {
            if (!(commandSourceStack.getSender() instanceof Player player)) {
                return false;
            }
            return player.isOp() || player.hasPermission("axiom.all") || player.hasPermission("axiom.debug") || player.getUniqueId().equals(MOULBERRY_UUID);
        };
        var canUseAxiomCommand = Commands.literal("canUseAxiom").requires(predicate).executes(context -> {
            if (!(context.getSource().getSender() instanceof Player player)) return 0;
            boolean canUseAxiom = axiomPaper.canUseAxiom(player);
            player.sendMessage(Component.text("canUseAxiom: " + canUseAxiom));
            return 1;
        });
        var isMismatchedDataVersionCommand = Commands.literal("isMismatchedDataVersion").requires(predicate).executes(context -> {
            if (!(context.getSource().getSender() instanceof Player player)) return 0;
            boolean isMismatchedDataVersion = axiomPaper.isMismatchedDataVersion(player.getUniqueId());
            player.sendMessage(Component.text("isMismatchedDataVersion: " + isMismatchedDataVersion));
            return 1;
        });
        var canModifyWorldCommand = Commands.literal("canModifyWorld").requires(predicate).executes(context -> {
            if (!(context.getSource().getSender() instanceof Player player)) return 0;
            boolean canModifyWorld = axiomPaper.canModifyWorld(player, player.getWorld());
            player.sendMessage(Component.text("canModifyWorld: " + canModifyWorld));
            return 1;
        });
        var isClientListeningCommand = Commands.literal("isClientListening")
                                               .then(Commands.argument("channel", StringArgumentType.greedyString()))
                                               .requires(predicate)
                                               .executes(context -> {
                                                   if (!(context.getSource().getSender() instanceof Player player)) return 0;
                                                   String channel = context.getArgument("channel", String.class);
                                                   boolean isClientListening = player.getListeningPluginChannels().contains(channel);
                                                   player.sendMessage(Component.text("listening to " + channel +": " + isClientListening));
                                                   return 1;
                                               });
        var hasPermissionCommand = Commands.literal("hasPermission")
                                               .then(Commands.argument("permission", StringArgumentType.greedyString()))
                                               .requires(predicate)
                                               .executes(context -> {
                                                   if (!(context.getSource().getSender() instanceof Player player)) return 0;
                                                   String permission = context.getArgument("permission", String.class);
                                                   boolean hasPermission = player.hasPermission(permission);
                                                   player.sendMessage(Component.text("has permission " + permission +": " + hasPermission));
                                                   return 1;
                                               });
        var getRestrictionsCommand = Commands.literal("getRestrictions").requires(predicate).executes(context -> {
            if (!(context.getSource().getSender() instanceof Player player)) return 0;
            Restrictions restrictions = axiomPaper.playerRestrictions.get(player.getUniqueId());
            if (restrictions == null) {
                player.sendMessage(Component.text("no restrictions"));
            } else {
                player.sendMessage(Component.text("restrictions: " + restrictions));
            }
            return 1;
        });
        var canBreakBlockAtCurrentPositionCommand = Commands.literal("canBreakBlockAtCurrentPosition").requires(predicate)
                                                            .executes(context -> {
                                                                if (!(context.getSource().getSender() instanceof Player player)) return 0;
                                                                Block block = player.getWorld().getBlockAt(player.getLocation());
                                                                boolean canBreakBlock = Integration.canBreakBlock(player, block);
                                                                player.sendMessage(Component.text("canBreakBlock: " + canBreakBlock));
                                                                return 1;
                                                            })
                                                            .then(Commands.literal("plotsquared").executes(context -> {
                                                                if (!(context.getSource().getSender() instanceof Player player)) return 0;
                                                                Block block = player.getWorld().getBlockAt(player.getLocation());
                                                                boolean canBreakBlock = PlotSquaredIntegration.canBreakBlock(player, block);
                                                                player.sendMessage(Component.text("canBreakBlock: " + canBreakBlock));
                                                                return 1;
                                                            }))
                                                            .then(Commands.literal("worldguard").executes(context -> {
                                                                if (!(context.getSource().getSender() instanceof Player player)) return 0;
                                                                boolean canBreakBlock = WorldGuardIntegration.canBreakBlock(player, player.getLocation());
                                                                player.sendMessage(Component.text("canBreakBlock: " + canBreakBlock));
                                                                return 1;
                                                            }));
        var canPlaceBlockAtCurrentPositionCommand = Commands.literal("canPlaceBlockAtCurrentPosition").requires(predicate)
                                                            .executes(context -> {
                                                                if (!(context.getSource().getSender() instanceof Player player)) return 0;
                                                                boolean canPlaceBlock = Integration.canPlaceBlock(player, player.getLocation());
                                                                player.sendMessage(Component.text("canPlaceBlock: " + canPlaceBlock));
                                                                return 1;
                                                            })
                                                            .then(Commands.literal("plotsquared").executes(context -> {
                                                                if (!(context.getSource().getSender() instanceof Player player)) return 0;
                                                                boolean canPlaceBlock = PlotSquaredIntegration.canPlaceBlock(player, player.getLocation());
                                                                player.sendMessage(Component.text("canPlaceBlock: " + canPlaceBlock));
                                                                return 1;
                                                            }))
                                                            .then(Commands.literal("worldguard").executes(context -> {
                                                                if (!(context.getSource().getSender() instanceof Player player)) return 0;
                                                                boolean canPlaceBlock = WorldGuardIntegration.canPlaceBlock(player, player.getLocation());
                                                                player.sendMessage(Component.text("canPlaceBlock: " + canPlaceBlock));
                                                                return 1;
                                                            }));
        var isPlotWorldCommand = Commands.literal("isPlotWorld").requires(predicate).executes(context -> {
            if (!(context.getSource().getSender() instanceof Player player)) return 0;
            boolean isPlotWorld = PlotSquaredIntegration.isPlotWorld(player.getWorld());
            player.sendMessage(Component.text("isPlotWorld: " + isPlotWorld));
            return 1;
        });
        var getCurrentEditablePlotCommand = Commands.literal("getCurrentEditablePlot").requires(predicate).executes(context -> {
            if (!(context.getSource().getSender() instanceof Player player)) return 0;
            PlotSquaredIntegration.PlotBounds plotBounds = PlotSquaredIntegration.getCurrentEditablePlot(player);
            player.sendMessage(Component.text("plotBounds: " + plotBounds));
            return 1;
        });

        var command = Commands.literal("axiompaperdebug").requires(predicate)
                              .then(canUseAxiomCommand)
                              .then(isMismatchedDataVersionCommand)
                              .then(canModifyWorldCommand)
                              .then(isClientListeningCommand)
                              .then(hasPermissionCommand)
                              .then(getRestrictionsCommand)
                              .then(canBreakBlockAtCurrentPositionCommand)
                              .then(canPlaceBlockAtCurrentPositionCommand)
                              .then(isPlotWorldCommand)
                              .then(getCurrentEditablePlotCommand)
                              .build();
        manager.register(command);
    }

}
