package me.approximations.apxPlugin.data.ormLite.repository.registry.discovery;

import me.approximations.apxPlugin.data.ormLite.repository.OrmLiteRepository;
import me.approximations.apxPlugin.reflection.DiscoveryService;
import me.approximations.apxPlugin.utils.ReflectionUtils;

import java.util.Set;

@SuppressWarnings("rawtypes")
public class OrmLiteRepositoryDiscoveryService implements DiscoveryService<Class<? extends OrmLiteRepository>> {
    public Set<Class<? extends OrmLiteRepository>> discoverAll() {
        return ReflectionUtils.getSubClassesOf(OrmLiteRepository.class);
    }
}

