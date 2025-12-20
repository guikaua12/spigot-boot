package tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.registration.BeanRegistrar;

public interface BeanDefinitionsReadyListener {
    /**
     * Called when all bean definitions have been discovered and registered,
     * but before instantiation begins.
     *
     * @param context            the context
     * @param definitionRegistry registry containing all bean definitions; while writable, callers should prefer using the provided registrar for adding definitions
     * @param registrar          registrar to add new bean definitions
     */
    void onBeanDefinitionsReady(@NotNull Context context,
                                @NotNull BeanDefinitionRegistry definitionRegistry,
                                @NotNull BeanRegistrar registrar);
}


