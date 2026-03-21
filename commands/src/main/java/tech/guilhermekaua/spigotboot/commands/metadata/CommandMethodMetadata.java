package tech.guilhermekaua.spigotboot.commands.metadata;

import tech.guilhermekaua.spigotboot.commands.CommandInterceptorAnnotationBinding;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    private final List<CommandInterceptorAnnotationBinding> interceptorBindings;
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
                                 List<CommandInterceptorAnnotationBinding> interceptorBindings,
                                 List<CommandParameterMetadata> parameters) {
        this.handlerBean = Objects.requireNonNull(handlerBean, "handlerBean must not be null");
        this.handlerType = Objects.requireNonNull(handlerType, "handlerType must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.aliases = Objects.requireNonNull(aliases, "aliases must not be null");
        this.description = description == null ? "" : description;
        this.usage = usage == null ? "" : usage;
        this.permission = permission == null ? "" : permission;
        this.completionIds = completionIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<>(completionIds));
        this.interceptorBindings = interceptorBindings == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<>(interceptorBindings));
        this.parameters = parameters == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<>(parameters));
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

    public List<CommandInterceptorAnnotationBinding> getInterceptorBindings() {
        return interceptorBindings;
    }

    public List<CommandParameterMetadata> getParameters() {
        return parameters;
    }
}
