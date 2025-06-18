package me.approximations.spigotboot.core.context.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.context.component.registry.ComponentRegistry;
import me.approximations.spigotboot.core.di.manager.DependencyManager;
import org.bukkit.plugin.Plugin;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

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

    public void registerComponents(Class<?>... baseClasses) {
        ComponentRegistry.RegisterResult registerResult = new ComponentRegistry(dependencyManager, plugin).registerComponents(baseClasses);
        dependencyManager.registerDependency(getClass(), this);
    }
}
