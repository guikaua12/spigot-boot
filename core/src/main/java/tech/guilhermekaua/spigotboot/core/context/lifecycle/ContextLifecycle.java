package tech.guilhermekaua.spigotboot.core.context.lifecycle;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.SpigotBoot;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.BeanDefinitionsReadyListener;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.core.context.registration.BeanRegistrar;
import tech.guilhermekaua.spigotboot.core.context.registration.DefaultBeanRegistrar;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.ModuleRegistry;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ContextLifecycle {
    private final Context context;
    private final DependencyManager dependencyManager;
    private final List<Class<? extends Module>> modulesToLoad;
    private final BeanRegistrar beanRegistrar;

    private ContextPhase currentPhase = ContextPhase.REGISTER_CORE;

    public ContextLifecycle(@NotNull Context context, @NotNull DependencyManager dependencyManager, @NotNull List<Class<? extends Module>> modulesToLoad) {
        this.context = context;
        this.dependencyManager = dependencyManager;
        this.modulesToLoad = modulesToLoad;

        beanRegistrar = new DefaultBeanRegistrar(dependencyManager, () -> {
            if (currentPhase.ordinal() >= ContextPhase.INSTANTIATE.ordinal()) {
                throw new IllegalStateException(
                        "Cannot register beans after INSTANTIATE phase. Current phase: " + currentPhase
                );
            }
        });
    }

    public void initialize() {
        try {
            runPhase(ContextPhase.REGISTER_CORE, this::registerCoreBeans);
            runPhase(ContextPhase.SCAN, this::scanPackages);
            runPhase(ContextPhase.MODULES, this::initializeModules);
            runPhase(ContextPhase.DEFINITIONS_READY, this::notifyBeanDefinitionsReady);
            runPhase(ContextPhase.INSTANTIATE, this::instantiateAllBeans);
            runPhase(ContextPhase.READY, this::notifyContextReady);
            currentPhase = ContextPhase.RUNNING;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize context", e);
        }
    }

    private void registerCoreBeans() {
        Plugin plugin = context.getPlugin();
        Logger logger = plugin.getLogger();

        beanRegistrar.registerInstance(logger, null, false);
        beanRegistrar.registerInstance(Logger.class, logger, null, false);
        beanRegistrar.registerInstance(Plugin.class, plugin, null, false);
        beanRegistrar.registerInstance(ProxyUtils.getRealClass(plugin), plugin, null, false);
        beanRegistrar.registerInstance(dependencyManager, null, false);
    }

    private void scanPackages() {
        Set<String> packagesToScan = new LinkedHashSet<>();
        packagesToScan.add(SpigotBoot.class.getPackage().getName());
        packagesToScan.add(ProxyUtils.getRealClass(context.getPlugin()).getPackage().getName());

        for (Class<? extends Module> moduleClass : modulesToLoad) {
            packagesToScan.add(moduleClass.getPackage().getName());
        }

        ComponentRegistry componentRegistry = dependencyManager.resolveDependency(ComponentRegistry.class, null, ComponentRegistry::new);
        ConfigurationProcessor configurationProcessor = dependencyManager.resolveDependency(ConfigurationProcessor.class, null, ConfigurationProcessor::new);
        MethodHandlerProcessor methodHandlerProcessor = dependencyManager.resolveDependency(MethodHandlerProcessor.class, null, MethodHandlerProcessor::new);

        for (String basePackage : packagesToScan) {
            componentRegistry.registerComponents(basePackage, dependencyManager);
            configurationProcessor.processFromPackage(basePackage, dependencyManager);
            MethodHandlerRegistry.registerAll(methodHandlerProcessor.processFromPackage(basePackage, dependencyManager));
        }
    }

    private void initializeModules() {
        ModuleRegistry moduleRegistry = dependencyManager.resolveDependency(ModuleRegistry.class, null);
        moduleRegistry.initializeModules(context, modulesToLoad);
    }

    private void notifyBeanDefinitionsReady() {
        BeanDefinitionRegistry registry = dependencyManager.getBeanDefinitionRegistry();
        for (Map.Entry<Class<?>, List<BeanDefinition>> entry : registry.asMapView().entrySet()) {
            Class<?> requestedType = entry.getKey();
            if (!BeanDefinitionsReadyListener.class.isAssignableFrom(requestedType)) {
                continue;
            }

            for (BeanDefinition definition : entry.getValue()) {
                if (!dependencyManager.getBeanInstanceRegistry().contains(definition)) {
                    try {
                        dependencyManager.resolveDependency(definition.getType(), definition.getQualifierName());
                    } catch (Exception e) {
                        context.getPlugin().getLogger().severe(
                                "Failed to instantiate BeanDefinitionsReadyListener: " + requestedType.getName()
                        );
                        e.printStackTrace();
                    }
                }
            }
        }

        List<BeanDefinitionsReadyListener> listeners = getOrderedListeners(BeanDefinitionsReadyListener.class);

        for (BeanDefinitionsReadyListener listener : listeners) {
            try {
                listener.onBeanDefinitionsReady(
                        context,
                        dependencyManager.getBeanDefinitionRegistry(),
                        beanRegistrar
                );
            } catch (Exception e) {
                context.getPlugin().getLogger().severe(
                        "Error executing BeanDefinitionsReadyListener: " + listener.getClass().getName()
                );
                e.printStackTrace();
            }
        }
    }

    private void instantiateAllBeans() {
        ComponentRegistry componentRegistry = dependencyManager.resolveDependency(
                ComponentRegistry.class, null
        );
        componentRegistry.resolveAllComponents(dependencyManager);

        dependencyManager.injectDependencies(
                ProxyUtils.getRealClass(context.getPlugin()),
                context.getPlugin()
        );
    }

    private void notifyContextReady() {
        List<ContextReadyListener> listeners = getOrderedListeners(ContextReadyListener.class);

        for (ContextReadyListener listener : listeners) {
            try {
                listener.onContextReady(context);
            } catch (Exception e) {
                context.getPlugin().getLogger().severe(
                        "Error executing ContextReadyListener: " + listener.getClass().getName()
                );
                e.printStackTrace();
            }
        }
    }

    private <T> List<T> getOrderedListeners(Class<T> listenerType) {
        List<T> listeners = dependencyManager.getInstancesByType(listenerType);
        if (listeners.isEmpty()) {
            return Collections.emptyList();
        }

        return listeners.stream()
                .sorted(Comparator.comparingInt(listener -> {
                    if (listener instanceof Ordered) {
                        return ((Ordered) listener).getOrder();
                    }
                    return 0;
                }))
                .collect(Collectors.toList());
    }

    private void runPhase(ContextPhase phase, Runnable action) {
        currentPhase = phase;
        action.run();
    }

    public ContextPhase getCurrentPhase() {
        return currentPhase;
    }

    public BeanRegistrar getBeanRegistrar() {
        return beanRegistrar;
    }
}

