package me.approximations.spigotboot.data.ormLite.registry.discovery;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.reflection.DiscoveryService;
import me.approximations.spigotboot.core.utils.ReflectionUtils;
import me.approximations.spigotboot.data.ormLite.repository.OrmLiteRepository;
import me.approximations.spigotboot.utils.ProxyUtils;
import org.bukkit.plugin.Plugin;

import java.util.Set;

@SuppressWarnings("rawtypes")
@RequiredArgsConstructor
public class OrmLiteRepositoryDiscoveryService implements DiscoveryService<Class<? extends OrmLiteRepository>> {
    private final Plugin plugin;

    public Set<Class<? extends OrmLiteRepository>> discoverAll() {
        return ReflectionUtils.getSubClassesOf(ProxyUtils.getRealClass(plugin), OrmLiteRepository.class);
    }
}

