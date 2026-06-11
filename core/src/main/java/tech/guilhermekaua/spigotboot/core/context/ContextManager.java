package tech.guilhermekaua.spigotboot.core.context;

import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class ContextManager {
    private final Map<BootPlugin, Context> contexts = new ConcurrentHashMap<>();

    public Context createContext(BootPlugin plugin) {
        if (contexts.containsKey(plugin)) {
            throw new IllegalStateException("Context already exists for plugin: " + plugin.getName());
        }

        PluginContext ctx = new PluginContext(plugin);
        contexts.put(plugin, ctx);
        return ctx;
    }

    public Context getContext(BootPlugin plugin) {
        return contexts.get(plugin);
    }
}