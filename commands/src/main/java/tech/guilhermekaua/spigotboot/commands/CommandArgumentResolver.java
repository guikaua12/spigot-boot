package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.Collection;
import java.util.Collections;

public interface CommandArgumentResolver<T> {
    boolean supports(CommandParameterMetadata parameter);

    T resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) throws Exception;

    default CommandCompletionProvider defaultCompletionProvider() {
        return null;
    }

    /**
     * Returns the message keys this resolver can raise via {@link CommandMessageException};
     * empty by default.
     *
     * @return the message keys declared by this resolver
     */
    default Collection<CommandMessageKey> messageKeys() {
        return Collections.emptyList();
    }
}
