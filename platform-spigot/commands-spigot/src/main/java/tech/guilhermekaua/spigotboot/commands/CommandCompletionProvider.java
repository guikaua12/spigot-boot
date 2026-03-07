package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.List;

public interface CommandCompletionProvider {
    List<String> complete(CommandExecutionContext context, CommandParameterMetadata parameter, String input);
}
