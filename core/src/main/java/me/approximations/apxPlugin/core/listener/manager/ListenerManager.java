package me.approximations.apxPlugin.core.listener.manager;

import me.approximations.apxPlugin.core.ApxPlugin;
import me.approximations.apxPlugin.core.di.manager.DependencyManager;
import me.approximations.apxPlugin.core.listener.annotations.ListenerRegister;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;
import me.approximations.apxPlugin.utils.ProxyUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ListenerManager {
    private final ApxPlugin plugin;
    private final DependencyManager dependencyManager;

    private final Set<Listener> registeredListeners = new HashSet<>();

    public ListenerManager(@NotNull ApxPlugin plugin, @NotNull DependencyManager dependencyManager) {
        this.plugin = plugin;
        this.dependencyManager = dependencyManager;

        registerListeners();
    }

    public void registerListeners() {
        for (final Class<? extends Listener> listener : ReflectionUtils.getSubClassesOf(ProxyUtils.getRealClass(plugin), Listener.class)) {
            if (!listener.isAnnotationPresent(ListenerRegister.class)) continue;

            try {
                final Listener instance = listener.newInstance();
                registerListener(instance);
//                dependencyManager.injectDependencies(instance);
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        plugin.addDisableEntry(this::unregisterListeners);
    }

    public void registerListener(Listener listener) {
        Objects.requireNonNull(listener, "listener cannot be null.");

        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    public void unregisterListeners() {
        for (final Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
            registeredListeners.remove(listener);
        }
    }
}
