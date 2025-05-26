package me.approximations.apxPlugin.context.component.registry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.di.annotations.Component;
import me.approximations.apxPlugin.di.manager.DependencyManager;
import me.approximations.apxPlugin.utils.ReflectionUtils;
import org.bukkit.plugin.Plugin;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class ComponentRegistry {
    private final DependencyManager dependencyManager;
    private final Plugin plugin;

    public RegisterResult registerComponents() {
        final Set<Class<? extends Annotation>> componentsAnnotations = discoverComponentsAnnotations();
        final Set<Class<?>> componentsClasses = discoverComponentsClasses(componentsAnnotations);

        for (Class<?> componentsClass : componentsClasses) {
            dependencyManager.registerDependency(componentsClass);
        }

        return new RegisterResult(componentsClasses, componentsAnnotations);
    }

    @SuppressWarnings("unchecked")
    private Set<Class<? extends Annotation>> discoverComponentsAnnotations() {
        return ReflectionUtils.getClassesFromPackage(ApxPlugin.class, plugin.getClass()).stream()
                .filter(clazz -> clazz.isAnnotation() && (clazz.equals(Component.class) || clazz.isAnnotationPresent(Component.class)))
                .map(clazz -> (Class<? extends Annotation>) clazz)
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> discoverComponentsClasses(Set<Class<? extends Annotation>> componentsAnnotations) {
        if (componentsAnnotations.isEmpty()) {
            return Collections.emptySet();
        }

        return ReflectionUtils.getClassesFromPackage(ApxPlugin.class, plugin.getClass())
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
