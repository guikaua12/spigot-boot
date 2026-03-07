package tech.guilhermekaua.spigotboot.commands.metadata;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public final class CommandMethodMetadata {
    private final Object handlerBean;
    private final Class<?> handlerType;
    private final Method method;
    private final CommandMethodKind kind;
    private final CommandAliasSet aliases;
    private final String description;
    private final String usage;
    private final String permission;
    private final List<String> completionIds;
    private final List<CommandParameterMetadata> parameters;

    public CommandMethodMetadata(Object handlerBean,
                                 Class<?> handlerType,
                                 Method method,
                                 CommandMethodKind kind,
                                 CommandAliasSet aliases,
                                 String description,
                                 String usage,
                                 String permission,
                                 List<String> completionIds,
                                 List<CommandParameterMetadata> parameters) {
        this.handlerBean = handlerBean;
        this.handlerType = handlerType;
        this.method = method;
        this.kind = kind;
        this.aliases = aliases;
        this.description = description == null ? "" : description;
        this.usage = usage == null ? "" : usage;
        this.permission = permission == null ? "" : permission;
        this.completionIds = completionIds == null ? Collections.<String>emptyList() : Collections.unmodifiableList(completionIds);
        this.parameters = parameters == null ? Collections.<CommandParameterMetadata>emptyList() : Collections.unmodifiableList(parameters);
    }

    public Object getHandlerBean() {
        return handlerBean;
    }

    public Class<?> getHandlerType() {
        return handlerType;
    }

    public Method getMethod() {
        return method;
    }

    public CommandMethodKind getKind() {
        return kind;
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

    public String getPermission() {
        return permission;
    }

    public List<String> getCompletionIds() {
        return completionIds;
    }

    public List<CommandParameterMetadata> getParameters() {
        return parameters;
    }
}
