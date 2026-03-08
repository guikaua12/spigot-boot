package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class CommandTextResolverChain {
    private static final CommandTextResolverChain EMPTY = new CommandTextResolverChain(Collections.emptyList());

    private final List<CommandTextResolver> resolvers;

    public CommandTextResolverChain(Collection<CommandTextResolver> resolvers) {
        this.resolvers = Collections.unmodifiableList(CommandSupport.sortBeans(resolvers));
    }

    public static CommandTextResolverChain empty() {
        return EMPTY;
    }

    public String resolve(CommandTextResolutionContext context, String value) {
        String resolved = value == null ? "" : value;
        for (CommandTextResolver resolver : resolvers) {
            resolved = resolver.resolve(context, resolved);
            if (resolved == null) {
                resolved = "";
            }
        }
        return resolved;
    }
}
