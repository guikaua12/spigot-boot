package tech.guilhermekaua.spigotboot.core.context.component.proxy.decider.strategy;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.decider.BeanProxyDecider;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.Objects;

public class BeanProxyDeciderResolver {
    private volatile boolean proxyDecidersBootstrapped = false;
    private final ThreadLocal<Boolean> bootstrappingProxyDeciders = ThreadLocal.withInitial(() -> false);

    public boolean shouldProxy(@NotNull BeanDefinition definition, @NotNull DependencyManager dependencyManager) throws Exception {
        Objects.requireNonNull(definition, "definition cannot be null.");
        Objects.requireNonNull(dependencyManager, "dependencyManager cannot be null.");

        Class<?> type = definition.getType();
        if (type == null) {
            return false;
        }

        // never proxy deciders themselves (avoids recursion and weird double-proxying)
        if (BeanProxyDecider.class.isAssignableFrom(type)) {
            return false;
        }

        ensureProxyDecidersBootstrapped(dependencyManager);

        for (BeanProxyDecider decider : dependencyManager.getInstancesByType(BeanProxyDecider.class)) {
            if (decider.shouldProxy(definition, dependencyManager)) {
                return true;
            }
        }

        return false;
    }

    private void ensureProxyDecidersBootstrapped(@NotNull DependencyManager dependencyManager) throws Exception {
        if (proxyDecidersBootstrapped) {
            return;
        }

        if (Boolean.TRUE.equals(bootstrappingProxyDeciders.get())) {
            return;
        }

        bootstrappingProxyDeciders.set(true);
        try {
            for (BeanDefinition definition : dependencyManager.getBeanDefinitionRegistry().getDefinitions(BeanProxyDecider.class)) {
                // resolve each definition directly to avoid qualifier/primary ambiguity
                dependencyManager.resolveFromDefinition(BeanProxyDecider.class, definition);
            }
            proxyDecidersBootstrapped = true;
        } finally {
            bootstrappingProxyDeciders.set(false);
        }
    }
}
