package me.approximations.apxPlugin.module;

import me.approximations.apxPlugin.di.annotations.Component;
import me.approximations.apxPlugin.utils.ReflectionUtils;

import java.util.Set;

@Component
public class ModuleDiscoveryService {
    public Set<Class<? extends Module>> discoverModules() {
        return ReflectionUtils.getSubClassesOf(Module.class);
    }
}
