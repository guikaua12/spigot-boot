package me.approximations.apxPlugin.core.module;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.di.annotations.Component;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;
import org.bukkit.plugin.Plugin;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class ModuleDiscoveryService {
    private final Plugin plugin;

    public Set<Class<? extends Module>> discoverModules() {
        return ReflectionUtils.getSubClassesOf(plugin.getClass(), Module.class);
    }
}
