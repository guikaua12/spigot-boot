package tech.guilhermekaua.spigotboot.commands.route;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandAliasSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompiledRootCommand {
    private final Object handlerBean;
    private final Class<?> handlerType;
    private final CommandAliasSet aliases;
    private final String description;
    private final String usage;
    private final List<CompiledCommandRoute> routes;
    private final CompiledCommandRoute defaultRoute;
    private final CompiledCommandRoute unknownRoute;

    public CompiledRootCommand(Object handlerBean,
                               Class<?> handlerType,
                               CommandAliasSet aliases,
                               String description,
                               String usage,
                               List<CompiledCommandRoute> routes,
                               CompiledCommandRoute defaultRoute,
                               CompiledCommandRoute unknownRoute) {
        this.handlerBean = handlerBean;
        this.handlerType = handlerType;
        this.aliases = aliases;
        this.description = description == null ? "" : description;
        this.usage = usage == null ? "" : usage;
        this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
        this.defaultRoute = defaultRoute;
        this.unknownRoute = unknownRoute;
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

    public List<CompiledCommandRoute> getRoutes() {
        return routes;
    }

    public CompiledCommandRoute getDefaultRoute() {
        return defaultRoute;
    }

    public CompiledCommandRoute getUnknownRoute() {
        return unknownRoute;
    }
}
