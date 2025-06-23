package tech.guilhermekaua.spigotboot.core.context;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class ContextManager {
    private final Map<JavaPlugin, PluginContext> contexts = new ConcurrentHashMap<>();
    @Getter
    private GlobalContext globalContext;

    public PluginContext createContext(JavaPlugin plugin) {
        if (contexts.containsKey(plugin)) {
            throw new IllegalStateException("Context already exists for plugin: " + plugin.getName());
        }

        if (globalContext == null) {
            globalContext = new GlobalContext(plugin);
            globalContext.initialize();
        }

        PluginContext ctx = new PluginContext(plugin, globalContext);
        contexts.put(plugin, ctx);
        return ctx;
    }

    public PluginContext getContext(JavaPlugin plugin) {
        return contexts.get(plugin);
    }
}