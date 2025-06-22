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
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class ComponentRegistry {
    private final Set<Class<? extends Annotation>> componentsAnnotations = new HashSet<>();
    private final DependencyManager dependencyManager;
    private final Plugin plugin;

    public ComponentRegistry(DependencyManager dependencyManager, Plugin plugin) {
        this.dependencyManager = dependencyManager;
        this.plugin = plugin;
        this.componentsAnnotations.addAll(discoverComponentsAnnotations());
    }

    public void registerComponents(Class<?>... baseClasses) {
        final Set<Class<?>> componentsClasses = discoverComponentsClasses(baseClasses);

        for (Class<?> componentsClass : componentsClasses) {
            dependencyManager.registerDependency(componentsClass, BeanUtils.getQualifier(componentsClass), BeanUtils.getIsPrimary(componentsClass), null);
        }
    }

    public void resolveAllComponents() {
        dependencyManager.getDependencyMap().values()
                .stream()
                .flatMap(Collection::stream)
                .filter(dep -> dep.getInstance() == null)
                .collect(Collectors.toList())
                .forEach(dep -> dependencyManager.resolveDependency(dep.getType(), dep.getQualifierName()));
    }

    @SuppressWarnings("unchecked")
    private Set<Class<? extends Annotation>> discoverComponentsAnnotations() {
        return ReflectionUtils.getClassesFromPackage(ApxPlugin.class, ProxyUtils.getRealClass(plugin)).stream()
                .filter(clazz -> clazz.isAnnotation() && (clazz.equals(Component.class) || clazz.isAnnotationPresent(Component.class)))
                .map(clazz -> (Class<? extends Annotation>) clazz)
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> discoverComponentsClasses(@Nullable Class<?>... baseClasses) {
        if (componentsAnnotations.isEmpty()) {
            return Collections.emptySet();
        }

        return ReflectionUtils.getClassesFromPackage(baseClasses)
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
