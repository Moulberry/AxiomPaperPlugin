package com.moulberry.axiom.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.commands.arguments.IntegrationTypeArgument;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import com.moulberry.axiom.integration.worldguard.WorldGuardIntegration;
import com.moulberry.axiom.restrictions.Restrictions;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

import static com.moulberry.axiom.commands.arguments.IntegrationTypeArgument.IntegrationType;

public class AxiomCommands {

    private static final UUID MOULBERRY_UUID = UUID.fromString("d0e05de7-6067-454d-beae-c6d19d886191");

    /**
     * Command requires either the 'axiom.debug' permission or for you to have the UUID d0e05de7-6067-454d-beae-c6d19d886191
     * The command isn't capable of modifying the world, only checking various properties for debugging purposes
     * It should be 100% safe to give to any player, but is locked behind some restrictions due to the potential
     * for lagging the server by spamming certain commands
     */
    public static LiteralCommandNode<CommandSourceStack> buildDebugCommand(AxiomPaper axiomPaper) {
        return Commands.literal("axiompaperdebug")
                .requires(s -> {
                    if (s.getSender() instanceof Player player) {
                        return player.isOp() || player.hasPermission("axiom.all") || player.hasPermission("axiom.debug") || player.getUniqueId().equals(MOULBERRY_UUID);
                    }
                    return false;
                })
                .then(Commands.literal("canUseAxiom")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                            boolean canUseAxiom = axiomPaper.canUseAxiom(player);
                            player.sendMessage(Component.text("canUseAxiom: " + canUseAxiom));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("isMismatchedDataVersion")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                            boolean isMismatchedDataVersion = axiomPaper.isMismatchedDataVersion(player.getUniqueId());
                            player.sendMessage(Component.text("isMismatchedDataVersion: " + isMismatchedDataVersion));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("canModifyWorld")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                            boolean canModifyWorld = axiomPaper.canModifyWorld(player, player.getWorld());
                            player.sendMessage(Component.text("canModifyWorld: " + canModifyWorld));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("isClientListening")
                        .then(Commands.argument("channel", StringArgumentType.greedyString())
                                .executes(context -> {
                                    if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                                    String channel = context.getArgument("channel", String.class);
                                    boolean isClientListening = player.getListeningPluginChannels().contains(channel);
                                    player.sendMessage(Component.text("listening to " + channel +": " + isClientListening));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("hasPermission")
                        .then(Commands.argument("permission", StringArgumentType.greedyString())
                                .executes(context -> {
                                    if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                                    String permission = context.getArgument("permission", String.class);
                                    boolean hasPermission = player.hasPermission(permission);
                                    player.sendMessage(Component.text("has permission " + permission +": " + hasPermission));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("getRestrictions")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                            Restrictions restrictions = axiomPaper.playerRestrictions.get(player.getUniqueId());
                            if (restrictions == null) {
                                player.sendMessage(Component.text("no restrictions"));
                            } else {
                                player.sendMessage(Component.text("restrictions: " + restrictions));
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("canBreakBlockAtCurrentPosition")
                        .then(Commands.argument("type", new IntegrationTypeArgument())
                                .executes(context -> {
                                    if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                                    IntegrationType integrationType = context.getArgument("type", IntegrationType.class);
                                    Block block = player.getWorld().getBlockAt(player.getLocation());

                                    boolean canBreakBlock = switch (integrationType) {
                                        case PLOT_SQUARED -> PlotSquaredIntegration.canBreakBlock(player, block);
                                        case WORLD_GUARD -> WorldGuardIntegration.canBreakBlock(player, block.getLocation());
                                    };

                                    player.sendMessage(Component.text("canBreakBlock: " + canBreakBlock));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                            Block block = player.getWorld().getBlockAt(player.getLocation());
                            player.sendMessage(Component.text("canBreakBlock: " + Integration.canBreakBlock(player, block)));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("canPlaceBlockAtCurrentPosition")
                        .then(Commands.argument("type", new IntegrationTypeArgument())
                                .executes(context -> {
                                    if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                                    IntegrationType integrationType = context.getArgument("type", IntegrationType.class);

                                    boolean canPlaceBlock = switch (integrationType) {
                                        case PLOT_SQUARED -> PlotSquaredIntegration.canPlaceBlock(player, player.getLocation());
                                        case WORLD_GUARD -> WorldGuardIntegration.canPlaceBlock(player, player.getLocation());
                                    };

                                    player.sendMessage(Component.text("canPlaceBlock: " + canPlaceBlock));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                            player.sendMessage(Component.text("canPlaceBlock: " + Integration.canPlaceBlock(player, player.getLocation())));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("isPlotWorld")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                            boolean isPlotWorld = PlotSquaredIntegration.isPlotWorld(player.getWorld());
                            player.sendMessage(Component.text("isPlotWorld: " + isPlotWorld));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("getCurrentEditablePlot")
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;
                            PlotSquaredIntegration.PlotBounds plotBounds = PlotSquaredIntegration.getCurrentEditablePlot(player);
                            player.sendMessage(Component.text("plotBounds: " + plotBounds));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildMigrateCommand() {
        return Commands.literal("axiompapermigrateconfig")
                .requires(s -> {
                    if (s.getSender() instanceof Player player) {
                        return player.isOp();
                    }
                    return s.getSender() instanceof ConsoleCommandSender;
                })
                .executes(context -> {
                    AxiomPaper.PLUGIN.migrateConfig(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

}
