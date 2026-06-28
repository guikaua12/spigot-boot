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
package tech.guilhermekaua.spigotboot.core.spigot.integrations;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registers the {@link EventHandler} methods of a proxied {@link Listener} bean against Bukkit.
 *
 * <p>{@link org.bukkit.plugin.PluginManager#registerEvents(Listener, Plugin)} discovers handler methods from
 * {@code listener.getClass()}. When the bean is a proxy, that subclass overrides every method, and the
 * overriding methods do not carry the original {@code @EventHandler} annotation, so Bukkit finds zero handlers and
 * the listener silently stops receiving events. This binder instead discovers the handler methods on the real
 * (unwrapped) class via {@link ProxyUtils#getRealClass(Object)} and registers each one individually, while still
 * invoking through the proxy instance so any method interception keeps applying.</p>
 */
class ProxiedListenerEventBinder {

    /**
     * Registers every {@code @EventHandler} method declared on the proxied listener's real class.
     *
     * @param plugin   the owning plugin, used as the registration owner.
     * @param listener the proxied listener bean to register; events are still dispatched through this proxy instance.
     */
    void register(@NotNull Plugin plugin, @NotNull Listener listener) {
        Class<?> realClass = ProxyUtils.getRealClass(listener);

        for (Method method : collectCandidateMethods(realClass)) {
            EventHandler eventHandler = method.getAnnotation(EventHandler.class);
            if (eventHandler == null || method.isBridge() || method.isSynthetic()) {
                continue;
            }

            Class<? extends Event> eventClass = resolveEventClass(method);
            if (eventClass == null) {
                // mirror bukkit's native diagnostic so a misconfigured handler on a proxied listener is not lost
                // silently (the plain registerEvents path logs the same case during startup).
                plugin.getLogger().severe("Attempted to register an invalid EventHandler method signature \""
                        + method.toGenericString() + "\" in " + realClass.getName());
                continue;
            }

            method.setAccessible(true);
            EventExecutor executor = createExecutor(method);

            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    eventHandler.priority(),
                    executor,
                    plugin,
                    eventHandler.ignoreCancelled()
            );
        }
    }

    // mirrors bukkit's JavaPluginLoader discovery: public (incl. inherited) plus declared methods, so private
    // @EventHandler methods are picked up too. a set dedupes the overlap between both collections.
    private Set<Method> collectCandidateMethods(Class<?> realClass) {
        Set<Method> methods = new LinkedHashSet<>();
        methods.addAll(Arrays.asList(realClass.getMethods()));
        methods.addAll(Arrays.asList(realClass.getDeclaredMethods()));
        return methods;
    }

    // a valid handler takes exactly one argument and that argument is an Event subtype.
    private Class<? extends Event> resolveEventClass(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1 || !Event.class.isAssignableFrom(parameterTypes[0])) {
            return null;
        }
        return parameterTypes[0].asSubclass(Event.class);
    }

    // invoking the real-class method on the proxy instance dispatches to the proxy override, so the method
    // handler chain (e.g. async/transactional interception) still runs before the real body executes.
    private EventExecutor createExecutor(Method method) {
        return (registeredListener, event) -> {
            try {
                method.invoke(registeredListener, event);
            } catch (InvocationTargetException ex) {
                throw new EventException(ex.getCause());
            } catch (Throwable t) {
                throw new EventException(t);
            }
        };
    }
}
