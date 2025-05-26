package me.approximations.apxPlugin.context.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.context.component.registry.ComponentRegistry;
import me.approximations.apxPlugin.di.annotations.Component;
import me.approximations.apxPlugin.di.manager.DependencyManager;
import org.bukkit.plugin.Plugin;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

@Component
@Getter
@RequiredArgsConstructor
public class ComponentManager {
    private final Set<Class<? extends Annotation>> componentsAnnotations = new HashSet<>();
    private final Set<Class<?>> componentsClasses = new HashSet<>();
    private final DependencyManager dependencyManager;
    private final Plugin plugin;

    public void registerComponents() {
        componentsAnnotations.clear();
        componentsClasses.clear();

        ComponentRegistry.RegisterResult registerResult = new ComponentRegistry(dependencyManager, plugin).registerComponents();
        dependencyManager.registerDependency(getClass(), this);

        componentsAnnotations.addAll(registerResult.getRegisteredAnnotations());
        componentsClasses.addAll(registerResult.getRegisteredClasses());
    }
}
