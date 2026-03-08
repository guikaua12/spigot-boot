package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

public interface CommandArgumentResolverRegistry {
    void register(CommandArgumentResolver<?> resolver);

    CommandArgumentResolver<?> resolve(CommandParameterMetadata parameter);
}
