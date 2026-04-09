package com.moulberry.axiom.commands;

import com.mojang.brigadier.context.CommandContext;
import com.moulberry.axiom.AxiomPaper;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.function.Predicate;

public class AxiomMigrateCommand {

    public static void register(Commands manager) {
        Predicate<CommandSourceStack> operatorPredicate = sender -> {
            if (sender instanceof Player player) {
                return player.isOp();
            } else {
                return sender instanceof ConsoleCommandSender;
            }
        };

        manager.register(Commands.literal("axiompapermigrateconfig")
            .requires(operatorPredicate)
            .executes(AxiomMigrateCommand::migrate)
            .build()
        );
    }

    private static int migrate(@NonNull CommandContext<CommandSourceStack> context) {
        AxiomPaper.PLUGIN.migrateConfig(context.getSource().getSender());
        return 1;
    }
}
