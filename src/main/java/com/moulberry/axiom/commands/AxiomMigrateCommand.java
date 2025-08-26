package com.moulberry.axiom.commands;

import com.moulberry.axiom.AxiomPaper;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.bukkit.BukkitCommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.permission.PredicatePermission;

public class AxiomMigrateCommand {

    public static void register(BukkitCommandManager<CommandSender> manager) {
        var operatorPredicate = PredicatePermission.of(sender -> {
            if (sender instanceof Player player) {
                return player.isOp();
            } else {
                return sender instanceof ConsoleCommandSender;
            }
        });

        var command = manager.commandBuilder("axiompapermigrateconfig")
                    .senderType(CommandSender.class)
                    .permission(operatorPredicate)
                    .handler(AxiomMigrateCommand::migrate);

        manager.command(command);
    }

    private static void migrate(@NonNull CommandContext<CommandSender> context) {
        AxiomPaper.PLUGIN.migrateConfig(context.sender());
    }
}
