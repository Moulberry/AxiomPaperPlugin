package com.moulberry.axiom.commands.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class IntegrationTypeArgument implements CustomArgumentType.Converted<IntegrationTypeArgument.@NotNull IntegrationType, @NotNull String> {

    public enum IntegrationType {
        PLOT_SQUARED,
        WORLD_GUARD
    }

    private static final DynamicCommandExceptionType ERROR_INVALID_INTEGRATION = new DynamicCommandExceptionType(type ->
            MessageComponentSerializer.message().serialize(Component.text(type + " is not a valid integration!"))
    );

    @Override
    public IntegrationType convert(String nativeType) throws CommandSyntaxException {
        try {
            return IntegrationType.valueOf(nativeType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw ERROR_INVALID_INTEGRATION.create(nativeType);
        }
    }

    @Override
    public <S> @NotNull CompletableFuture<Suggestions> listSuggestions(@NotNull CommandContext<S> context, @NotNull SuggestionsBuilder builder) {
        for (IntegrationType type : IntegrationType.values()) {
            String word = type.toString();
            if (word.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(word);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public @NotNull ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

}
