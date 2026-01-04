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
package tech.guilhermekaua.spigotboot.config.spigot.proxy;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * A proxy that intercepts all method calls on a config class and delegates to
 * the current config instance from {@link ConfigRef#get()}.
 * <p>
 * This ensures that injected config instances always return fresh values after reload,
 * without requiring developers to use {@code ConfigRef<T>.get()} explicitly.
 * <p>
 * <b>Important:</b> Config classes must have all fields private to ensure proper
 * proxying. Direct field access bypasses the proxy and will return stale/default values.
 *
 * @param <T> the config type
 */
public final class ConfigProxy<T> implements MethodHandler {

    private final ConfigRef<T> configRef;

    private ConfigProxy(@NotNull ConfigRef<T> configRef) {
        this.configRef = Objects.requireNonNull(configRef, "configRef cannot be null");
    }

    /**
     * Creates a proxy for the given config class that delegates all method calls
     * to the current config instance from the ConfigRef.
     *
     * @param configClass the config class to proxy
     * @param configRef   the config ref providing the current instance
     * @param <T>         the config type
     * @return a proxy instance that appears as the config class
     * @throws ConfigException if proxy creation fails
     */
    @SuppressWarnings("unchecked")
    public static <T> @NotNull T createProxy(@NotNull Class<T> configClass, @NotNull ConfigRef<T> configRef) {
        Objects.requireNonNull(configClass, "configClass cannot be null");
        Objects.requireNonNull(configRef, "configRef cannot be null");

        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(configClass);
        factory.setUseWriteReplace(false);

        try {
            return (T) factory.create(new Class<?>[0], new Object[0], new ConfigProxy<>(configRef));
        } catch (NoSuchMethodException e) {
            throw new ConfigException(
                    "Config class " + configClass.getName() + " must have a no-arg constructor for proxying. " +
                            "Add a default constructor or use @Inject on an existing constructor.", e);
        } catch (Exception e) {
            throw new ConfigException(
                    "Failed to create config proxy for " + configClass.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        String methodName = thisMethod.getName();

        if (thisMethod.getDeclaringClass() == Object.class) {
            switch (methodName) {
                case "toString":
                    return "ConfigProxy[" + configRef.getConfigClass().getSimpleName() + "]@" +
                            Integer.toHexString(System.identityHashCode(self));
                case "hashCode":
                    return System.identityHashCode(self);
                case "equals":
                    if (args[0] instanceof ConfigProxy) {
                        return self == args[0];
                    }
                    return false;
                default:
                    return proceed.invoke(self, args);
            }
        }

        T currentConfig = configRef.get();
        return thisMethod.invoke(currentConfig, args);
    }
}
