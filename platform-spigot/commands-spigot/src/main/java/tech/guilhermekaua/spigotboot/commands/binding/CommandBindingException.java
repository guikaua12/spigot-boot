package tech.guilhermekaua.spigotboot.commands.binding;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

public class CommandBindingException extends RuntimeException {
    public enum Kind {
        MISSING_ARGUMENT,
        INVALID_ARGUMENT,
        SENDER_MISMATCH
    }

    private final Kind kind;
    private final CommandParameterMetadata parameter;
    private final String input;
    private final Class<?> expectedSenderType;

    private CommandBindingException(Kind kind,
                                    CommandParameterMetadata parameter,
                                    String input,
                                    Class<?> expectedSenderType,
                                    Throwable cause) {
        super(cause);
        this.kind = kind;
        this.parameter = parameter;
        this.input = input;
        this.expectedSenderType = expectedSenderType;
    }

    public static CommandBindingException missing(CommandParameterMetadata parameter) {
        return new CommandBindingException(Kind.MISSING_ARGUMENT, parameter, null, null, null);
    }

    public static CommandBindingException invalid(CommandParameterMetadata parameter, String input, Throwable cause) {
        return new CommandBindingException(Kind.INVALID_ARGUMENT, parameter, input, null, cause);
    }

    public static CommandBindingException senderMismatch(CommandParameterMetadata parameter, Class<?> expectedSenderType) {
        return new CommandBindingException(Kind.SENDER_MISMATCH, parameter, null, expectedSenderType, null);
    }

    public Kind getKind() {
        return kind;
    }

    public CommandParameterMetadata getParameter() {
        return parameter;
    }

    public String getInput() {
        return input;
    }

    public Class<?> getExpectedSenderType() {
        return expectedSenderType;
    }
}
