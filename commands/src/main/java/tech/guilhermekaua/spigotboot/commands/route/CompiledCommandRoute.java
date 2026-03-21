package tech.guilhermekaua.spigotboot.commands.route;

import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandMethodKind;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPattern;

public final class CompiledCommandRoute {
    private final CommandMethodKind kind;
    private final CommandPattern pattern;
    private final CommandInvocationPlan invocationPlan;
    private final String description;
    private final String usage;
    private final String permission;

    public CompiledCommandRoute(CommandMethodKind kind,
                                CommandPattern pattern,
                                CommandInvocationPlan invocationPlan,
                                String description,
                                String usage,
                                String permission) {
        this.kind = kind;
        this.pattern = pattern;
        this.invocationPlan = invocationPlan;
        this.description = description == null ? "" : description;
        this.usage = usage == null ? "" : usage;
        this.permission = permission == null ? "" : permission;
    }

    public CommandMethodKind getKind() {
        return kind;
    }

    public CommandPattern getPattern() {
        return pattern;
    }

    public CommandInvocationPlan getInvocationPlan() {
        return invocationPlan;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usage;
    }

    public String getPermission() {
        return permission;
    }

    public boolean isGreedy() {
        for (CommandPattern.CommandSegment segment : pattern.getSegments()) {
            if (segment instanceof CommandPattern.ArgumentSegment) {
                if (invocationPlan.getParsedBinding(((CommandPattern.ArgumentSegment) segment).getName()).isGreedy()) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getMinimumTokenCount() {
        return pattern.getMinimumTokenCount();
    }

    public int getMaximumTokenCount() {
        return isGreedy() ? Integer.MAX_VALUE : pattern.getSegments().size();
    }
}
