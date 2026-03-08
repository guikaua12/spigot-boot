package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

public interface CommandArgumentResolver<T> {
    boolean supports(CommandParameterMetadata parameter);

    T resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) throws Exception;

    default CommandCompletionProvider defaultCompletionProvider() {
        return null;
    }
}
