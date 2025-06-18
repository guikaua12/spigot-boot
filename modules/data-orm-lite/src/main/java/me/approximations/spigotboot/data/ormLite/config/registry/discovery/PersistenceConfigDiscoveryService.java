package me.approximations.spigotboot.data.ormLite.config.registry.discovery;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.reflection.DiscoveryService;
import me.approximations.spigotboot.core.utils.ReflectionUtils;
import me.approximations.spigotboot.data.ormLite.config.PersistenceConfig;
import me.approximations.spigotboot.utils.ProxyUtils;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
public class PersistenceConfigDiscoveryService implements DiscoveryService<Class<? extends PersistenceConfig>> {
    private final Plugin plugin;

    public Optional<Class<? extends PersistenceConfig>> discover() {
        final Set<Class<? extends PersistenceConfig>> configs = ReflectionUtils.getSubClassesOf(ProxyUtils.getRealClass(plugin), PersistenceConfig.class);

        if (configs.isEmpty()) return Optional.empty();

        if (configs.size() > 1) {
            throw new IllegalStateException("Multiple PersistenceConfig implementations currently not supported, found: (" + configs.stream().map(Class::getName).reduce((a, b) -> a + ", " + b).get() + ")");
        }

        return Optional.of(configs.iterator().next());
    }
}
