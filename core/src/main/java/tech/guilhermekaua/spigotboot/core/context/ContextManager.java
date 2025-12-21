package tech.guilhermekaua.spigotboot.core.context;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.module.Module;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class ContextManager {
    private final Map<JavaPlugin, Context> contexts = new ConcurrentHashMap<>();

    @SafeVarargs
    public final Context createContext(JavaPlugin plugin, @NotNull Class<? extends Module>... modulesToLoad) {
        if (contexts.containsKey(plugin)) {
            throw new IllegalStateException("Context already exists for plugin: " + plugin.getName());
        }

        PluginContext ctx = new PluginContext(plugin, modulesToLoad);
        contexts.put(plugin, ctx);
        return ctx;
    }

    public Context getContext(JavaPlugin plugin) {
        return contexts.get(plugin);
    }
}