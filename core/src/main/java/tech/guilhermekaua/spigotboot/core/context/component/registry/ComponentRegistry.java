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
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.scanner.ClassPathScanner;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class ComponentRegistry {
    private final Set<Class<? extends Annotation>> componentsAnnotations = new HashSet<>();

    public void registerComponents(String basePackage, DependencyManager dependencyManager) {
        this.componentsAnnotations.addAll(discoverComponentsAnnotations(basePackage));

        Set<Class<?>> componentsClasses = discoverComponentsClasses(basePackage);

        for (Class<?> componentsClass : componentsClasses) {
            dependencyManager.registerDependency(
                    componentsClass,
                    BeanUtils.getQualifier(componentsClass),
                    BeanUtils.getIsPrimary(componentsClass),
                    null,
                    BeanUtils.createDependencyReloadCallback(componentsClass)
            );
        }
    }

    public void resolveAllComponents(DependencyManager dependencyManager) {
        for (Map.Entry<Class<?>, List<BeanDefinition>> entry : dependencyManager.getBeanDefinitionRegistry().asMapView().entrySet()) {
            for (BeanDefinition definition : entry.getValue()) {
                if (dependencyManager.getBeanInstanceRegistry().contains(definition)) {
                    continue;
                }

                dependencyManager.resolveDependency(definition.getType(), definition.getQualifierName());
            }
        }
    }

    private Set<Class<? extends Annotation>> discoverComponentsAnnotations(String basePackage) {
        ClassPathScanner scanner = new ClassPathScanner(getClass().getClassLoader(), basePackage);

        return Stream.concat(
                        Stream.of(Component.class),
                        scanner.getTypesAnnotatedWith(Component.class)
                                .stream()
                                .filter(Class::isAnnotation)
                ).map(clazz -> (Class<? extends Annotation>) clazz)
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> discoverComponentsClasses(@NotNull String... basePackages) {
        if (componentsAnnotations.isEmpty()) {
            return Collections.emptySet();
        }

        ClassPathScanner scanner = new ClassPathScanner(getClass().getClassLoader(), basePackages);

        return componentsAnnotations.stream()
                .map(scanner::getTypesAnnotatedWith)
                .flatMap(Collection::stream)
                .filter(clazz -> !clazz.isInterface() && !clazz.isEnum() && !clazz.isAnnotation())
                .collect(Collectors.toSet());
    }
}
