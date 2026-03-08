package tech.guilhermekaua.spigotboot.commands.execution;

import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandMethodMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPattern;

public class CommandInvocationFactory {
    private final CommandParameterRoleResolver roleResolver;

    public CommandInvocationFactory(CommandParameterRoleResolver roleResolver) {
        this.roleResolver = roleResolver;
    }

    public CommandInvocationPlan create(CommandMethodMetadata methodMetadata, CommandPattern pattern) {
        return new CommandInvocationPlan(
                methodMetadata.getHandlerBean(),
                methodMetadata.getMethod(),
                roleResolver.resolve(methodMetadata, pattern),
                methodMetadata.getInterceptorBindings()
        );
    }
}
