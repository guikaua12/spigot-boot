package me.approximations.spigotboot.placeholder.registry.discovery;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.reflection.DiscoveryService;
import me.approximations.spigotboot.core.utils.ReflectionUtils;
import me.approximations.spigotboot.placeholder.annotations.RegisterPlaceholder;
import me.approximations.spigotboot.utils.ProxyUtils;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

@RequiredArgsConstructor
public class PlaceholderDiscoveryService implements DiscoveryService<Class<?>> {
    private final Plugin plugin;

    @Override
    public Collection<Class<?>> discoverAll() {
        return ReflectionUtils.getClassesAnnotatedWith(ProxyUtils.getRealClass(plugin), RegisterPlaceholder.class);
    }
}
