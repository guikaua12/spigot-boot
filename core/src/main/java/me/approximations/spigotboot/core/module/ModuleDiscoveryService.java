package me.approximations.spigotboot.core.module;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.di.annotations.Component;
import me.approximations.spigotboot.core.utils.ReflectionUtils;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class ModuleDiscoveryService {
    public Set<Class<? extends Module>> discoverModules() {
        return ReflectionUtils.getSubClassesOf(Module.class);
    }
}
