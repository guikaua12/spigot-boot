package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.Collection;
import java.util.Optional;

public interface CommandArgumentResolverRegistry {
    void register(CommandArgumentResolver<?> resolver);

    Optional<CommandArgumentResolver<?>> resolve(CommandParameterMetadata parameter);

    /**
     * Returns an unmodifiable snapshot of all registered resolvers.
     *
     * @return all registered resolvers
     */
    Collection<CommandArgumentResolver<?>> all();
}
