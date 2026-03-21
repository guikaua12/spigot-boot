package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.Optional;

public interface CommandArgumentResolverRegistry {
    void register(CommandArgumentResolver<?> resolver);

    Optional<CommandArgumentResolver<?>> resolve(CommandParameterMetadata parameter);
}
