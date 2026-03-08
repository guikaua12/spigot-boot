package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CommandRootCompiler {
    private final CommandHandlerIntrospector commandHandlerIntrospector;
    private final CommandRouteFactory routeFactory;
    private final CommandRouteValidator routeValidator;
    private final CommandInterceptorChain commandInterceptorChain;

    public CommandRootCompiler(CommandHandlerIntrospector commandHandlerIntrospector,
                               CommandRouteFactory routeFactory,
                               CommandRouteValidator routeValidator,
                               CommandInterceptorChain commandInterceptorChain) {
        this.commandHandlerIntrospector = commandHandlerIntrospector;
        this.routeFactory = routeFactory;
        this.routeValidator = routeValidator;
        this.commandInterceptorChain = commandInterceptorChain;
    }

    public List<CompiledRootCommand> compile(Context context) {
        List<Object> handlers = collectHandlers(context);
        if (handlers.isEmpty()) {
            return new ArrayList<>();
        }

        List<RootCommandMetadata> metadata = new ArrayList<>();
        for (Object handler : handlers) {
            metadata.add(commandHandlerIntrospector.introspect(handler, context.getDependencyManager()));
        }

        List<CompiledRootCommand> roots = routeFactory.create(metadata);
        routeValidator.validate(roots);
        commandInterceptorChain.validate(context, roots);
        return roots;
    }

    private List<Object> collectHandlers(Context context) {
        Collection<Object> instances = context.getDependencyManager().getBeanInstanceRegistry().asMapView().values();
        List<Object> handlers = new ArrayList<>();
        for (Object instance : CommandSupport.deduplicateByIdentity(instances)) {
            if (ProxyUtils.getRealClass(instance).isAnnotationPresent(CommandHandler.class)) {
                handlers.add(instance);
            }
        }
        return handlers;
    }
}
