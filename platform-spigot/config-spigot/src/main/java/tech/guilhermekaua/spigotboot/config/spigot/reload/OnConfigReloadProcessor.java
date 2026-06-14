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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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

    // scans the whole class hierarchy so an @OnConfigReload method declared protected or
    // package-private on a superclass is still discovered (getMethods alone only sees public
    // inherited ones, getDeclaredMethods only the concrete class). dedupes by signature keeping the
    // most-derived declaration, so an overridden + re-annotated callback is registered only once.
    private static Collection<Method> collectMethods(Class<?> realClass) {
        Map<String, Method> bySignature = new LinkedHashMap<>();
        for (Method method : realClass.getMethods()) {
            bySignature.putIfAbsent(signatureOf(method), method);
        }
        for (Class<?> current = realClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                bySignature.putIfAbsent(signatureOf(method), method);
            }
        }
        return bySignature.values();
    }

    private static String signatureOf(Method method) {
        StringBuilder signature = new StringBuilder(method.getName());
        for (Class<?> parameterType : method.getParameterTypes()) {
            signature.append('|').append(parameterType.getName());
        }
        return signature.toString();
    }
}
