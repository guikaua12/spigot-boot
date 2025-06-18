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
package tech.guilhermekaua.spigotboot.core.context.component.registry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.ApxPlugin;
import tech.guilhermekaua.spigotboot.core.di.annotations.Component;
import tech.guilhermekaua.spigotboot.core.di.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class ComponentRegistry {
    private final DependencyManager dependencyManager;
    private final Plugin plugin;

    public RegisterResult registerComponents(@Nullable Class<?>... baseClasses) {
        final Set<Class<? extends Annotation>> componentsAnnotations = discoverComponentsAnnotations();
        final Set<Class<?>> componentsClasses = discoverComponentsClasses(componentsAnnotations, baseClasses);

        for (Class<?> componentsClass : componentsClasses) {
            dependencyManager.registerDependency(componentsClass);
        }

        return new RegisterResult(componentsClasses, componentsAnnotations);
    }

    public RegisterResult registerComponents() {
        return registerComponents(null);
    }

    @SuppressWarnings("unchecked")
    private Set<Class<? extends Annotation>> discoverComponentsAnnotations() {
        return ReflectionUtils.getClassesFromPackage(ApxPlugin.class, ProxyUtils.getRealClass(plugin)).stream()
                .filter(clazz -> clazz.isAnnotation() && (clazz.equals(Component.class) || clazz.isAnnotationPresent(Component.class)))
                .map(clazz -> (Class<? extends Annotation>) clazz)
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> discoverComponentsClasses(Set<Class<? extends Annotation>> componentsAnnotations, @Nullable Class<?>... baseClasses) {
        if (componentsAnnotations.isEmpty()) {
            return Collections.emptySet();
        }

        Class<?>[] baseClassesToUse = baseClasses != null ? baseClasses : new Class<?>[]{ApxPlugin.class, ProxyUtils.getRealClass(plugin)};

        return ReflectionUtils.getClassesFromPackage(baseClassesToUse)
                .stream()
                .filter(clazz -> !clazz.isInterface() && !clazz.isEnum() && !clazz.isAnnotation())
                .filter(clazz -> componentsAnnotations.stream().anyMatch(clazz::isAnnotationPresent))
                .collect(Collectors.toSet());
    }

    @RequiredArgsConstructor
    @Getter
    public static class RegisterResult {
        private final Set<Class<?>> registeredClasses;
        private final Set<Class<? extends Annotation>> registeredAnnotations;

    }
}
