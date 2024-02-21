package me.approximations.apxPlugin.placeholder.register;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.di.manager.DependencyManager;
import me.approximations.apxPlugin.placeholder.Placeholder;
import me.approximations.apxPlugin.placeholder.manager.PlaceholderManager;
import me.approximations.apxPlugin.placeholder.papi.PAPIExpansion;
import org.bukkit.Bukkit;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.logging.Level;

@RequiredArgsConstructor
public class PlaceholderRegister {
    public static final String PAPI_NAME = "PlaceholderAPI";

    private final ApxPlugin plugin;
    private final Reflections reflections;
    private final DependencyManager dependencyManager;
    private final PlaceholderManager placeholderManager;
    private PAPIExpansion papiExpansion;

    public void register() {
        if (!Bukkit.getPluginManager().isPluginEnabled(PAPI_NAME)) {
            plugin.getLogger().log(Level.INFO, "PlaceholderAPI not found, skipping registration of placeholders");
        } else {
            papiExpansion = new PAPIExpansion(plugin, placeholderManager);
            papiExpansion.register();

            plugin.addDisableEntry(() -> papiExpansion.unregister());
        }

        final Set<Class<? extends Placeholder>> placeholderClasses = getPlaceholders();

        for (Class<? extends Placeholder> placeholderClass : placeholderClasses) {
            try {
                final Placeholder placeholder = placeholderClass.getConstructor().newInstance();

                placeholderManager.register(placeholder);
                dependencyManager.registerDependency(placeholderClass, placeholder);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        plugin.addDisableEntry(placeholderManager::clear);
    }

    private Set<Class<? extends Placeholder>> getPlaceholders() {
        return reflections.getSubTypesOf(Placeholder.class);
    }
}
