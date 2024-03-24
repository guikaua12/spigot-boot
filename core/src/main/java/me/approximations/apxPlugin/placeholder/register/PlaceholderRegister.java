package me.approximations.apxPlugin.placeholder.register;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.di.manager.DependencyManager;
import me.approximations.apxPlugin.placeholder.Placeholder;
import me.approximations.apxPlugin.placeholder.manager.PlaceholderManager;
import me.approximations.apxPlugin.utils.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class PlaceholderRegister {

    private final ApxPlugin plugin;
    private final DependencyManager dependencyManager;

    public void register() {
        final Set<Class<? extends Placeholder>> placeholderClasses = getPlaceholders();
        final Set<Placeholder> placeholders = new HashSet<>();

        for (Class<? extends Placeholder> placeholderClass : placeholderClasses) {
            try {
                final Placeholder placeholder = placeholderClass.getConstructor().newInstance();
                placeholders.add(placeholder);

                dependencyManager.registerDependency(placeholderClass, placeholder);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        if (PlaceholderManager.init(plugin, placeholders.toArray(new Placeholder[1]))) {
            plugin.addDisableEntry(() -> PlaceholderManager.getPapiExpansion().unregister());
            plugin.addDisableEntry(PlaceholderManager::clear);
        }
    }

    private Set<Class<? extends Placeholder>> getPlaceholders() {
        return ReflectionUtils.getSubClassesOf(Placeholder.class);
    }
}
