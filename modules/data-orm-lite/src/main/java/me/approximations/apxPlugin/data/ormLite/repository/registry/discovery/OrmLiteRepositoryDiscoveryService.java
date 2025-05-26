package me.approximations.apxPlugin.data.ormLite.repository.registry.discovery;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.data.ormLite.repository.OrmLiteRepository;
import me.approximations.apxPlugin.core.reflection.DiscoveryService;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;
import org.bukkit.plugin.Plugin;

import java.util.Set;

@SuppressWarnings("rawtypes")
@RequiredArgsConstructor
public class OrmLiteRepositoryDiscoveryService implements DiscoveryService<Class<? extends OrmLiteRepository>> {
    private final Plugin plugin;

    public Set<Class<? extends OrmLiteRepository>> discoverAll() {
        return ReflectionUtils.getSubClassesOf(plugin.getClass(), OrmLiteRepository.class);
    }
}

