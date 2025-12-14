package tech.guilhermekaua.spigotboot.core;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.ContextManager;
import tech.guilhermekaua.spigotboot.core.module.Module;

import java.util.Objects;

public final class SpigotBoot {
    private static final ContextManager CONTEXT_MANAGER = new ContextManager();

    @SafeVarargs
    public static Context initialize(@NotNull JavaPlugin plugin, @NotNull Class<? extends Module>... modulesToLoad) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(modulesToLoad, "modulesToLoad cannot be null");

        Context ctx = CONTEXT_MANAGER.getContext(plugin);
        if (ctx == null) {
            ctx = CONTEXT_MANAGER.createContext(plugin, modulesToLoad);
        }

        if (ctx.isInitialized()) {
            throw new IllegalStateException("Context is already initialized for plugin: " + plugin.getName());
        }

        ctx.initialize();
        return ctx;
    }

    public static Context getContext(@NotNull JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        return CONTEXT_MANAGER.getContext(plugin);
    }

    public static void onDisable(@NotNull JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        Context context = CONTEXT_MANAGER.getContext(plugin);
        if (context == null || !context.isInitialized()) {
            throw new IllegalStateException("Context is not initialized for plugin: " + plugin.getName());
        }

        context.destroy();
    }

    public static void registerShutdownHook(@NotNull JavaPlugin plugin, @NotNull Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(runnable, "runnable cannot be null");

        Context context = CONTEXT_MANAGER.getContext(plugin);
        if (context == null || !context.isInitialized()) {
            throw new IllegalStateException("Context is not initialized for plugin: " + plugin.getName());
        }

        context.registerShutdownHook(runnable);
    }

    public static void unregisterShutdownHook(@NotNull JavaPlugin plugin, @NotNull Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(runnable, "runnable cannot be null");

        Context context = CONTEXT_MANAGER.getContext(plugin);
        if (context == null || !context.isInitialized()) {
            throw new IllegalStateException("Context is not initialized for plugin: " + plugin.getName());
        }

        context.unregisterShutdownHook(runnable);
    }
}
