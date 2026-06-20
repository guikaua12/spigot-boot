package tech.guilhermekaua.spigotboot.commands.message;

import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Opt-in, read-only catalog of every {@link CommandMessageKey} declared by the registered
 * argument resolvers. Useful for programmatic enumeration; performs no logging.
 */
public class CommandMessageCatalog {
    private final List<CommandMessageKey> keys = new ArrayList<>();

    public CommandMessageCatalog(CommandArgumentResolverRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        for (CommandArgumentResolver<?> resolver : registry.all()) {
            keys.addAll(resolver.messageKeys());
        }
    }

    /**
     * Returns an unmodifiable view of all declared keys.
     *
     * @return all declared message keys
     */
    public Collection<CommandMessageKey> all() {
        return Collections.unmodifiableList(keys);
    }

    /**
     * Finds the first key whose {@link CommandMessageKey#id()} matches the given id.
     *
     * @param id the key id to look up
     * @return the first key with a matching id, if any
     */
    public Optional<CommandMessageKey> find(String id) {
        for (CommandMessageKey key : keys) {
            if (key.id().equals(id)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }
}
