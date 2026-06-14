package tech.guilhermekaua.spigotboot.core.spigot.integrations;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class BukkitListenerAutoRegistrar implements ContextReadyListener {
    private final List<Listener> autoRegisteredListeners = new ArrayList<>();
    private final ProxiedListenerEventBinder proxiedListenerEventBinder;

    @Inject
    public BukkitListenerAutoRegistrar() {
        this(new ProxiedListenerEventBinder());
    }

    BukkitListenerAutoRegistrar(@NotNull ProxiedListenerEventBinder proxiedListenerEventBinder) {
        this.proxiedListenerEventBinder = Objects.requireNonNull(proxiedListenerEventBinder,
                "proxiedListenerEventBinder cannot be null");
    }

    @Override
    public void onContextReady(@NotNull Context context) {
        Plugin plugin = context.getBean(Plugin.class);

        List<Listener> listenerBeans = context.getBeansByType(Listener.class);

        for (Listener listener : listenerBeans) {
            // a proxied listener hands bukkit a subclass whose method overrides have dropped the @EventHandler
            // annotation, so registering it natively discovers no handlers. bind those handlers from the real
            // class instead, keeping the proxy instance as the invocation target.
            if (ProxyUtils.isProxy(listener)) {
                proxiedListenerEventBinder.register(plugin, listener);
            } else {
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            }
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
