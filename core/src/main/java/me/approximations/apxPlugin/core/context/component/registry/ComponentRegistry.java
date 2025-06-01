package me.approximations.apxPlugin.core.context.component.registry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.ApxPlugin;
import me.approximations.apxPlugin.core.di.annotations.Component;
import me.approximations.apxPlugin.core.di.manager.DependencyManager;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;
import me.approximations.apxPlugin.utils.ProxyUtils;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

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
