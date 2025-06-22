/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.core.listener.manager;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.ApxPlugin;
import tech.guilhermekaua.spigotboot.core.context.annotations.ListenerRegister;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

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
