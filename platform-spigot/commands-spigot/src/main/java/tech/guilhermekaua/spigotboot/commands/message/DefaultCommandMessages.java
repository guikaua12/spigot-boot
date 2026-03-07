package tech.guilhermekaua.spigotboot.commands.message;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessages;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

public class DefaultCommandMessages implements CommandMessages {
    @Override
    public String missingRequiredArgument(CommandExecutionContext context, CommandParameterMetadata parameter) {
        return "Missing required argument: " + parameter.getLookupName();
    }

    @Override
    public String invalidArgumentValue(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        return "Invalid value '" + input + "' for argument: " + parameter.getLookupName();
    }

    @Override
    public String noPermission(CommandExecutionContext context, String permission) {
        return "You do not have permission to use this command.";
    }

    @Override
    public String senderTypeMismatch(CommandExecutionContext context, Class<?> expectedSenderType) {
        return "Only " + expectedSenderType.getSimpleName() + " may use this command.";
    }

    @Override
    public String unknownSubcommand(CommandExecutionContext context) {
        return "Unknown subcommand.";
    }

    @Override
    public String usage(CommandExecutionContext context, String usage) {
        return "Usage: " + usage;
    }

    @Override
    public String executionError(CommandExecutionContext context, Throwable throwable) {
        return "An internal error occurred while executing this command.";
    }
}
