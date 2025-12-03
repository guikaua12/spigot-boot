package tech.guilhermekaua.spigotboot.core;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.ContextManager;
import tech.guilhermekaua.spigotboot.core.context.GlobalContext;
import tech.guilhermekaua.spigotboot.core.context.PluginContext;

import java.util.Objects;

public final class SpigotBoot {
    private static final ContextManager CONTEXT_MANAGER = new ContextManager();

    public static void initialize(@NotNull JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        GlobalContext globalContext = CONTEXT_MANAGER.createGlobalContextIfNotExists(plugin);

        if (!globalContext.isInitialized()) {
            globalContext.initialize();
        }

        PluginContext ctx = CONTEXT_MANAGER.getContext(plugin);
        if (ctx == null) {
            ctx = CONTEXT_MANAGER.createContext(plugin);
        }

        if (ctx.isInitialized()) {
            throw new IllegalStateException("Context is already initialized for plugin: " + plugin.getName());
        }

        ctx.initialize();
    }

    public static PluginContext getContext(@NotNull JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        return CONTEXT_MANAGER.getContext(plugin);
    }

    public static void onDisable(@NotNull JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        PluginContext context = CONTEXT_MANAGER.getContext(plugin);
        if (context == null || !context.isInitialized()) {
            throw new IllegalStateException("Context is not initialized for plugin: " + plugin.getName());
        }

        context.destroy();

        GlobalContext globalContext = CONTEXT_MANAGER.getGlobalContext();
        if (plugin.equals(globalContext.getPlugin())) {
            CONTEXT_MANAGER.getGlobalContext().destroy();
        }
    }

    public static void registerShutdownHook(@NotNull JavaPlugin plugin, @NotNull Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(runnable, "runnable cannot be null");

        PluginContext context = CONTEXT_MANAGER.getContext(plugin);
        if (context == null || !context.isInitialized()) {
            throw new IllegalStateException("Context is not initialized for plugin: " + plugin.getName());
        }

        context.registerShutdownHook(runnable);
    }

    public static void unregisterShutdownHook(@NotNull JavaPlugin plugin, @NotNull Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(runnable, "runnable cannot be null");

        PluginContext context = CONTEXT_MANAGER.getContext(plugin);
        if (context == null || !context.isInitialized()) {
            throw new IllegalStateException("Context is not initialized for plugin: " + plugin.getName());
        }

        context.unregisterShutdownHook(runnable);
    }
}
