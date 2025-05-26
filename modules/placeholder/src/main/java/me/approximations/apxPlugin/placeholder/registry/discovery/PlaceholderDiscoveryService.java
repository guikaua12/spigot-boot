package me.approximations.apxPlugin.placeholder.registry.discovery;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.placeholder.annotations.RegisterPlaceholder;
import me.approximations.apxPlugin.reflection.DiscoveryService;
import me.approximations.apxPlugin.utils.ReflectionUtils;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

@RequiredArgsConstructor
public class PlaceholderDiscoveryService implements DiscoveryService<Class<?>> {
    private final Plugin plugin;

    @Override
    public Collection<Class<?>> discoverAll() {
        return ReflectionUtils.getClassesAnnotatedWith(plugin.getClass(), RegisterPlaceholder.class);
    }
}
