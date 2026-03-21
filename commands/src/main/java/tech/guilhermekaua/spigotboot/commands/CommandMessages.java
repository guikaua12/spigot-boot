package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.time.Duration;

public interface CommandMessages {
    String missingRequiredArgument(CommandExecutionContext context, CommandParameterMetadata parameter);

    String invalidArgumentValue(CommandExecutionContext context, CommandParameterMetadata parameter, String input);

    String noPermission(CommandExecutionContext context, String permission);

    String senderTypeMismatch(CommandExecutionContext context, Class<?> expectedSenderType);

    String unknownSubcommand(CommandExecutionContext context);

    String usage(CommandExecutionContext context, String usage);

    String executionError(CommandExecutionContext context, Throwable throwable);

    default String onCooldown(CommandExecutionContext context, Duration remaining) {
        if (remaining == null) {
            throw new IllegalArgumentException("remaining duration must not be null");
        }
        long millis = Math.max(0L, remaining.toMillis());
        if (millis >= 1000L) {
            long seconds = (millis + 999L) / 1000L;
            return "You must wait " + seconds + "s before using this command again.";
        }
        return "You must wait " + millis + "ms before using this command again.";
    }
}
