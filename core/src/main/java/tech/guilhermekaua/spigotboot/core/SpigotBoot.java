package tech.guilhermekaua.spigotboot.core;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.ContextManager;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.List;
import java.util.Objects;

public final class SpigotBoot {
    private static final ContextManager CONTEXT_MANAGER = new ContextManager();

    public static Context initialize(@NotNull BootPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        return builder(plugin).autoDiscover().initialize();
    }

    @SafeVarargs
    public static Context initialize(@NotNull BootPlugin plugin, @NotNull Class<? extends Module>... modulesToLoad) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(modulesToLoad, "modulesToLoad cannot be null");
        return builder(plugin).modules(modulesToLoad).initialize();
    }

    public static SpigotBootBuilder builder(@NotNull BootPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        return new SpigotBootBuilder(plugin);
    }

    static Context doInitialize(BootPlugin plugin, List<Class<? extends Module>> modules) {
        Context ctx = CONTEXT_MANAGER.getContext(plugin);
        if (ctx == null) {
            ctx = CONTEXT_MANAGER.createContext(plugin);
        }

        if (ctx.isInitialized()) {
            throw new IllegalStateException("Context is already initialized for plugin: " + plugin.getName());
        }

        ctx.setModulesToLoad(modules);
        ctx.initialize();
        return ctx;
    }

    public static Context getContext(@NotNull BootPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        return CONTEXT_MANAGER.getContext(plugin);
    }

    public static void onDisable(@NotNull BootPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        Context context = CONTEXT_MANAGER.getContext(plugin);
        if (context == null || !context.isInitialized()) {
            return;
        }

        context.destroy();
    }

    public static void registerShutdownHook(@NotNull BootPlugin plugin, @NotNull Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(runnable, "runnable cannot be null");

        Context context = CONTEXT_MANAGER.getContext(plugin);
        if (context == null || !context.isInitialized()) {
            throw new IllegalStateException("Context is not initialized for plugin: " + plugin.getName());
        }

        context.registerShutdownHook(runnable);
    }

    public static void unregisterShutdownHook(@NotNull BootPlugin plugin, @NotNull Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(runnable, "runnable cannot be null");

        Context context = CONTEXT_MANAGER.getContext(plugin);
        if (context == null || !context.isInitialized()) {
            throw new IllegalStateException("Context is not initialized for plugin: " + plugin.getName());
        }

        context.unregisterShutdownHook(runnable);
    }
}
