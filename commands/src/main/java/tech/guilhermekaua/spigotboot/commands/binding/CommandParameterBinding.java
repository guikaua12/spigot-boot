package tech.guilhermekaua.spigotboot.commands.binding;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

public final class CommandParameterBinding {
    private final CommandParameterMetadata metadata;
    private final CommandParameterRole role;
    private final String tokenName;
    private final boolean optional;
    private final boolean greedy;
    private final String completionId;

    public CommandParameterBinding(CommandParameterMetadata metadata,
                                   CommandParameterRole role,
                                   String tokenName,
                                   boolean optional,
                                   boolean greedy,
                                   String completionId) {
        this.metadata = metadata;
        this.role = role;
        this.tokenName = tokenName;
        this.optional = optional;
        this.greedy = greedy;
        this.completionId = completionId == null ? "" : completionId.trim();
    }

    public CommandParameterMetadata getMetadata() {
        return metadata;
    }

    public CommandParameterRole getRole() {
        return role;
    }

    public String getTokenName() {
        return tokenName;
    }

    public boolean isOptional() {
        return optional;
    }

    public boolean isGreedy() {
        return greedy;
    }

    public String getCompletionId() {
        return completionId;
    }

    public CommandParameterBinding withCompletionId(String completionId) {
        return new CommandParameterBinding(metadata, role, tokenName, optional, greedy, completionId);
    }
}
