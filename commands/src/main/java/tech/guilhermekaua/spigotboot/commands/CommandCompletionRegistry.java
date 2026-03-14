package tech.guilhermekaua.spigotboot.commands;

import java.util.Optional;

public interface CommandCompletionRegistry {
    void register(String id, CommandCompletionProvider provider);

    /**
     * Resolves a completion provider by its registered id.
     *
     * @param id the completion id to look up
     * @return the provider wrapped in an {@link Optional}, or an empty {@link Optional} if no
     *         provider is registered under the given id (including when id is {@code null})
     */
    Optional<CommandCompletionProvider> resolve(String id);
}
