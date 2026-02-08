package tech.guilhermekaua.spigotboot.core.context;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class ContextManager {
    private final Map<BootPlugin, Context> contexts = new ConcurrentHashMap<>();

    @SafeVarargs
    public final Context createContext(BootPlugin plugin, @NotNull Class<? extends Module>... modulesToLoad) {
        if (contexts.containsKey(plugin)) {
            throw new IllegalStateException("Context already exists for plugin: " + plugin.getName());
        }

        PluginContext ctx = new PluginContext(plugin, modulesToLoad);
        contexts.put(plugin, ctx);
        return ctx;
    }

    public Context getContext(BootPlugin plugin) {
        return contexts.get(plugin);
    }
}