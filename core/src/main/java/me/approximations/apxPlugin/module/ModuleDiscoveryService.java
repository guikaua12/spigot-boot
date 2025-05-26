package me.approximations.apxPlugin.module;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.di.annotations.Component;
import me.approximations.apxPlugin.utils.ReflectionUtils;
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
