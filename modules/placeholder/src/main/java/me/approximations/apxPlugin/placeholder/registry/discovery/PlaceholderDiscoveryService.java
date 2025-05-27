package me.approximations.apxPlugin.placeholder.registry.discovery;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.reflection.DiscoveryService;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;
import me.approximations.apxPlugin.placeholder.annotations.RegisterPlaceholder;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

@RequiredArgsConstructor
public class PlaceholderDiscoveryService implements DiscoveryService<Class<?>> {
    private final Plugin plugin;

    @Override
    public Collection<Class<?>> discoverAll() {
        return ReflectionUtils.getClassesAnnotatedWith(ReflectionUtils.getRealPluginClass(plugin), RegisterPlaceholder.class);
    }
}
