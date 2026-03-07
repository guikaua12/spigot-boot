package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

public interface CommandMessages {
    String missingRequiredArgument(CommandExecutionContext context, CommandParameterMetadata parameter);

    String invalidArgumentValue(CommandExecutionContext context, CommandParameterMetadata parameter, String input);

    String noPermission(CommandExecutionContext context, String permission);

    String senderTypeMismatch(CommandExecutionContext context, Class<?> expectedSenderType);

    String unknownSubcommand(CommandExecutionContext context);

    String usage(CommandExecutionContext context, String usage);

    String executionError(CommandExecutionContext context, Throwable throwable);
}
