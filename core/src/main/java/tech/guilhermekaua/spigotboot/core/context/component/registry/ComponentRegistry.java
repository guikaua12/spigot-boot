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
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionEvaluator;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyResolveResolver;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryIndexReader;
import tech.guilhermekaua.spigotboot.core.scanner.ClassPathScanner;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class ComponentRegistry {
    private static final Logger LOGGER = Logger.getLogger(ComponentRegistry.class.getName());

    private final Set<Class<? extends Annotation>> componentsAnnotations = new HashSet<>();
    private DiscoveryIndexReader discoveryIndexReader;

    public void registerComponents(String basePackage, DependencyManager dependencyManager) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ComponentRegistry.class.getClassLoader();
        }

        ConditionContext conditionContext = new SimpleConditionContext(
                dependencyManager.getBeanDefinitionRegistry(),
                null,
                classLoader
        );

        // Index covers minimize-jar-safe classes; classpath scan covers package-private classes
        // the index can't reference (test fixtures, inner classes). Union both for completeness.
        componentsAnnotations.addAll(discoverComponentsAnnotations(basePackage));

        Set<Class<?>> componentsClasses = new LinkedHashSet<>(discoverComponentsClasses(basePackage));

        DiscoveryIndexReader reader = getDiscoveryIndexReader();
        if (reader.hasAnyIndex()) {
            componentsAnnotations.add(Component.class);
            componentsClasses.addAll(reader.classesInCategory(DiscoveryCategories.COMPONENT, basePackage));
        }

        for (Class<?> componentsClass : componentsClasses) {
            if (ConditionEvaluator.shouldSkip(componentsClass, conditionContext, "ComponentRegistry")) {
                continue;
            }

            registerScannedComponent(componentsClass, dependencyManager);
        }
    }

    private DiscoveryIndexReader getDiscoveryIndexReader() {
        if (discoveryIndexReader == null) {
            discoveryIndexReader = DiscoveryIndexReader.create();
        }
        return discoveryIndexReader;
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

    /**
     * Registers a single scanned component, tolerating components that cannot be linked because a type
     * they reference comes from an absent optional dependency.
     * <p>
     * A component may link fine itself yet declare a constructor parameter, field, or setter whose type
     * is supplied only by a soft dependency (for example {@code PlaceholderRegistry}, whose constructor
     * takes a {@code PAPIExpansion} that extends {@code me.clip.placeholderapi.expansion.PlaceholderExpansion}).
     * The cycle-detection pre-scan forces those member types to link, which throws
     * {@link NoClassDefFoundError} when the optional dependency is absent. Skipping the offending
     * component keeps a single unavailable component from aborting the whole boot, mirroring the
     * soft-dependency resilience the discovery index already has (see
     * {@code tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryIndexSupport}).
     *
     * @param componentClass    the scanned component class to register, not null
     * @param dependencyManager the dependency manager to register the component into, not null
     * @return {@code true} if the component was registered, {@code false} if it was skipped because it
     * references a type from an absent optional dependency
     */
    @SuppressWarnings("unchecked")
    boolean registerScannedComponent(@NotNull Class<?> componentClass, @NotNull DependencyManager dependencyManager) {
        try {
            registerScannedComponentTyped((Class<Object>) componentClass, dependencyManager);
            return true;
        } catch (NoClassDefFoundError e) {
            // the component references a type from an absent optional dependency, so linking it (here,
            // while the cycle-detection pre-scan resolves its member types) fails. skip it so one
            // unavailable component does not poison the whole scan. only NoClassDefFoundError is caught
            // (not the broader LinkageError) so genuinely broken classes -- VerifyError, ClassFormatError,
            // UnsupportedClassVersionError -- still fail the boot loudly instead of being silently skipped.
            LOGGER.log(Level.FINE, "Skipping component '" + componentClass.getName()
                    + "': references a type from an absent optional dependency", e);
            return false;
        }
    }

    private <T> void registerScannedComponentTyped(@NotNull Class<T> componentClass,
                                                   @NotNull DependencyManager dependencyManager) {
        dependencyManager.registerDependency(
                componentClass,
                BeanUtils.getQualifier(componentClass),
                BeanUtils.getIsPrimary(componentClass),
                (DependencyResolveResolver<T>) null
        );
    }
}
