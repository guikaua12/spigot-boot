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
package tech.guilhermekaua.spigotboot.core.bungee.integrations;

import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-registers every {@link Listener} bean with BungeeCord when the context is ready, and
 * unregisters them on shutdown. Mirrors the Spigot {@code BukkitListenerAutoRegistrar}.
 *
 * <p>Known limitation: a proxied listener bean (one the container wrapped to intercept methods) has its
 * {@code @EventHandler} overrides stripped of the annotation, and BungeeCord exposes no per-method
 * registration to rebind them. Such a listener is registered natively but logged as a warning, since
 * its handlers may not fire. Plain (non-proxied) listeners are unaffected.
 */
@Component
public class BungeeListenerAutoRegistrar implements ContextReadyListener {
    private final List<Listener> autoRegisteredListeners = new ArrayList<>();

    @Override
    public void onContextReady(@NotNull Context context) {
        Plugin plugin = context.getBean(Plugin.class);
        PluginManager pluginManager = plugin.getProxy().getPluginManager();

        List<Listener> listenerBeans = context.getBeansByType(Listener.class);

        for (Listener listener : listenerBeans) {
            if (ProxyUtils.isProxy(listener)) {
                plugin.getLogger().warning("Proxied listener " + ProxyUtils.getRealClass(listener).getName()
                        + " may not receive events on BungeeCord (proxied listeners are not fully supported yet).");
            }
            pluginManager.registerListener(plugin, listener);
            autoRegisteredListeners.add(listener);
        }

        context.registerShutdownHook(() -> {
            for (Listener listener : autoRegisteredListeners) {
                pluginManager.unregisterListener(listener);
            }
            autoRegisteredListeners.clear();
        });
    }
}
