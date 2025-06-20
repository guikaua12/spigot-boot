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
package tech.guilhermekaua.spigotboot.core.di.manager;

import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.di.ClassMetadata;
import tech.guilhermekaua.spigotboot.di.DIContainer;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;

@RequiredArgsConstructor
public class DependencyManager {
    private final DIContainer diContainer = new DIContainer();
    private final Plugin plugin;

    @SuppressWarnings("unchecked")
    public <T> T resolveDependency(@NotNull Class<T> clazz) {
        try {
            Objects.requireNonNull(clazz, "class cannot be null.");

            if (!diContainer.getTypeMappings().containsKey(clazz)) {
                return null;
            }

            if (diContainer.getSingletons().containsKey(clazz)) {
                return clazz.cast(diContainer.getSingletons().get(clazz));
            }

            ClassMetadata<T> implMetadata = (ClassMetadata<T>) diContainer.getTypeMappings().get(clazz);
            if (implMetadata.getClazz().isInterface()) {
                T componentProxy = ComponentProxy.createProxy(implMetadata.getClazz(), new Class[0], new Object[0], plugin);
                diContainer.getSingletons().put(clazz, componentProxy);
                return componentProxy;
            }

            Constructor<?> injectConstructor = implMetadata.getInjectConstructor();

            Object[] objects = Arrays.stream(injectConstructor.getParameterTypes()).map(diContainer::resolve).toArray();

            T componentProxy = ComponentProxy.createProxy(implMetadata.getClazz(), injectConstructor.getParameterTypes(), objects, plugin);
            diContainer.getSingletons().put(clazz, componentProxy);
            diContainer.injectDependencies(componentProxy);

            return componentProxy;
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve type: " + clazz, e);
        }
    }

    public void injectDependencies(@NotNull Object instance) {
        Objects.requireNonNull(instance, "instance cannot be null.");

        try {
            diContainer.injectDependencies(instance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject dependencies for instance: " + instance.getClass(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull T dependency) {
        Objects.requireNonNull(dependency, "dependency cannot be null.");

        return (T) registerDependency(dependency.getClass(), dependency);
    }

    public <T> T registerDependency(@NotNull Class<? extends T> clazz, @NotNull T dependency) {
        Objects.requireNonNull(dependency, "dependency cannot be null.");

        diContainer.register(clazz, dependency);

        return dependency;
    }

    public <T> void registerDependency(@NotNull Class<T> clazz) {
        try {
            Objects.requireNonNull(clazz, "clazz cannot be null.");

            diContainer.register(clazz, clazz);
            if (clazz.getSuperclass() != null && clazz.getSuperclass().isInterface()) {
                registerDependency(clazz.getSuperclass(), clazz);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to register dependency using class: " + clazz, e);
        }
    }

    public <T> void registerDependency(@NotNull Class<T> clazz, @NotNull Class<? extends T> dependencyClass) {
        Objects.requireNonNull(clazz, "clazz cannot be null.");
        Objects.requireNonNull(dependencyClass, "dependencyClass cannot be null.");

        diContainer.register(clazz, dependencyClass);
    }

}