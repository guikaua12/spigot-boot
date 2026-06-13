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
package tech.guilhermekaua.spigotboot.config.spigot.reload;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessor;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@link BeanPostProcessor} that wires {@link OnConfigReload} methods of DI-managed beans to config
 * reload listeners. Runs after {@code MethodHandlerProxyBeanPostProcessor} so it binds to the final
 * (possibly proxied) bean instance.
 */
public class OnConfigReloadProcessor implements BeanPostProcessor {

    private final OnConfigReloadBinder binder;

    /**
     * Creates the processor.
     *
     * @param configManager the config manager, not null
     * @param logger        the plugin logger, not null
     */
    public OnConfigReloadProcessor(@NotNull SpigotConfigManager configManager, @NotNull Logger logger) {
        Objects.requireNonNull(configManager, "configManager cannot be null");
        Objects.requireNonNull(logger, "logger cannot be null");
        this.binder = new OnConfigReloadBinder(configManager, new OnConfigReloadInvoker(logger));
    }

    @Override
    public @NotNull Object postProcess(@NotNull BeanDefinition definition,
                                       @NotNull Object instance,
                                       @NotNull DependencyManager dependencyManager) {
        Class<?> realClass = ProxyUtils.getRealClass(instance);
        for (Method method : collectMethods(realClass)) {
            if (method.isAnnotationPresent(OnConfigReload.class)) {
                binder.bind(instance, method);
            }
        }
        return instance;
    }

    @Override
    public int getOrder() {
        // after MethodHandlerProxyBeanPostProcessor (default Ordered order 0) so we bind to the proxy
        return 100;
    }

    // mirrors the bukkit listener discovery: public (incl. inherited) plus declared methods, so
    // private @OnConfigReload methods are picked up too. a set dedupes the overlap.
    private static Set<Method> collectMethods(Class<?> realClass) {
        Set<Method> methods = new LinkedHashSet<>();
        methods.addAll(Arrays.asList(realClass.getMethods()));
        methods.addAll(Arrays.asList(realClass.getDeclaredMethods()));
        return methods;
    }
}
