package tech.guilhermekaua.spigotboot.core.integrations.bukkit;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;

import java.util.ArrayList;
import java.util.List;

@Component
public class BukkitListenerAutoRegistrar implements ContextReadyListener {
    private final List<Listener> autoRegisteredListeners = new ArrayList<>();

    @Override
    public void onContextReady(@NotNull Context context) {
        Plugin plugin = context.getPlugin();

        List<Listener> listenerBeans = context.getBeansByType(Listener.class);

        for (Listener listener : listenerBeans) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            autoRegisteredListeners.add(listener);
        }

        context.registerShutdownHook(() -> {
            for (Listener listener : autoRegisteredListeners) {
                HandlerList.unregisterAll(listener);
            }
            autoRegisteredListeners.clear();
        });
    }
}


