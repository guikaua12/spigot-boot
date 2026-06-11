package tech.guilhermekaua.spigotboot.commands.spigot;

import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandRootCompiler;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;

import java.util.List;

public class CommandsContextReadyRegistrar implements ContextReadyListener, Ordered {
    private final CommandRootCompiler commandRootCompiler;
    private final BukkitCommandRegistrar bukkitCommandRegistrar;
    private final CommandReplacementRegistry replacementRegistry;

    public CommandsContextReadyRegistrar(CommandRootCompiler commandRootCompiler,
                                         BukkitCommandRegistrar bukkitCommandRegistrar,
                                         CommandReplacementRegistry replacementRegistry) {
        this.commandRootCompiler = commandRootCompiler;
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

        List<CompiledRootCommand> roots = commandRootCompiler.compile(context);
        if (roots.isEmpty()) {
            return;
        }

        final RegisteredCommandSet registered = bukkitCommandRegistrar.register(context, roots);
        context.registerShutdownHook(() -> bukkitCommandRegistrar.unregister(registered));
    }
}
