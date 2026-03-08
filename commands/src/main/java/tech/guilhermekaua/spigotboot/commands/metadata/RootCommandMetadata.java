package tech.guilhermekaua.spigotboot.commands.metadata;

import java.util.Collections;
import java.util.List;

public final class RootCommandMetadata {
    private final Object handlerBean;
    private final Class<?> handlerType;
    private final CommandAliasSet aliases;
    private final String description;
    private final String usage;
    private final List<CommandMethodMetadata> methods;

    public RootCommandMetadata(Object handlerBean,
                               Class<?> handlerType,
                               CommandAliasSet aliases,
                               String description,
                               String usage,
                               List<CommandMethodMetadata> methods) {
        this.handlerBean = handlerBean;
        this.handlerType = handlerType;
        this.aliases = aliases;
        this.description = description == null ? "" : description;
        this.usage = usage == null ? "" : usage;
        this.methods = methods == null ? Collections.<CommandMethodMetadata>emptyList() : Collections.unmodifiableList(methods);
    }

    public Object getHandlerBean() {
        return handlerBean;
    }

    public Class<?> getHandlerType() {
        return handlerType;
    }

    public CommandAliasSet getAliases() {
        return aliases;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usage;
    }

    public List<CommandMethodMetadata> getMethods() {
        return methods;
    }
}
