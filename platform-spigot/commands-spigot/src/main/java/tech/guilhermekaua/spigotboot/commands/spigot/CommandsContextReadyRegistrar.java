package tech.guilhermekaua.spigotboot.commands.spigot;

import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CommandsContextReadyRegistrar implements ContextReadyListener, Ordered {
    private final CommandHandlerIntrospector commandHandlerIntrospector;
    private final CommandRouteFactory routeFactory;
    private final CommandRouteValidator routeValidator;
    private final BukkitCommandRegistrar bukkitCommandRegistrar;
    private final CommandReplacementRegistry replacementRegistry;

    public CommandsContextReadyRegistrar(CommandHandlerIntrospector commandHandlerIntrospector,
                                         CommandRouteFactory routeFactory,
                                         CommandRouteValidator routeValidator,
                                         BukkitCommandRegistrar bukkitCommandRegistrar,
                                         CommandReplacementRegistry replacementRegistry) {
        this.commandHandlerIntrospector = commandHandlerIntrospector;
        this.routeFactory = routeFactory;
        this.routeValidator = routeValidator;
        this.bukkitCommandRegistrar = bukkitCommandRegistrar;
        this.replacementRegistry = replacementRegistry;
    }

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public void onContextReady(Context context) {
        replacementRegistry.register("plugin.name", context.getPlugin().getName());
        replacementRegistry.register("plugin", context.getPlugin().getName());

        List<Object> handlers = collectHandlers(context);
        if (handlers.isEmpty()) {
            return;
        }

        List<RootCommandMetadata> metadata = new ArrayList<>();
        for (Object handler : handlers) {
            metadata.add(commandHandlerIntrospector.introspect(handler));
        }

        List<CompiledRootCommand> roots = routeFactory.create(metadata);
        routeValidator.validate(roots);

        final RegisteredCommandSet registered = bukkitCommandRegistrar.register(context, roots);
        context.registerShutdownHook(() -> bukkitCommandRegistrar.unregister(registered));
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
