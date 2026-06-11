package tech.guilhermekaua.spigotboot.core.context.lifecycle;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.SpigotBoot;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.BeanDefinitionsReadyListener;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.core.context.registration.BeanRegistrar;
import tech.guilhermekaua.spigotboot.core.context.registration.DefaultBeanRegistrar;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.ModuleRegistry;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ContextLifecycle {
    private final Context context;
    private final DependencyManager dependencyManager;
    private final List<Class<? extends Module>> modulesToLoad;
    private final BeanRegistrar beanRegistrar;
    private final BeanLifecycleInvoker beanLifecycleInvoker;

    private ContextPhase currentPhase = ContextPhase.REGISTER_CORE;

    public ContextLifecycle(@NotNull Context context, @NotNull DependencyManager dependencyManager, @NotNull List<Class<? extends Module>> modulesToLoad) {
        this.context = context;
        this.dependencyManager = dependencyManager;
        this.modulesToLoad = modulesToLoad;
        this.beanLifecycleInvoker = new BeanLifecycleInvoker(dependencyManager);

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
            invokeOnEnableCallbacks();
            runPhase(ContextPhase.READY, this::notifyContextReady);
            currentPhase = ContextPhase.RUNNING;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize context", e);
        }
    }

    private void registerCoreBeans() {
        BootPlugin bootPlugin = context.getPlugin();
        Logger logger = bootPlugin.getLogger();

        beanRegistrar.registerInstance(logger, null, false);
        beanRegistrar.registerInstance(Logger.class, logger, null, false);
        beanRegistrar.registerInstance(BootPlugin.class, bootPlugin, null, false);
        beanRegistrar.registerInstance(context, null, false);
        beanRegistrar.registerInstance(dependencyManager, null, false);

        CustomInjectorRegistry customInjectorRegistry = dependencyManager.getCustomInjectorRegistry();
        beanRegistrar.registerInstance(CustomInjectorRegistry.class, customInjectorRegistry, null, false);
    }

    private void scanPackages() {
        MethodHandlerRegistry.clear();

        Set<String> rawPackagesToScan = new LinkedHashSet<>();
        rawPackagesToScan.add(SpigotBoot.class.getPackage().getName());
        rawPackagesToScan.add(context.getPlugin().getMainClass().getPackage().getName());

        for (Class<? extends Module> moduleClass : modulesToLoad) {
            rawPackagesToScan.add(moduleClass.getPackage().getName());
        }
        List<String> packagesToScan = minimizePackageRoots(rawPackagesToScan);

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

        Object nativePlugin = context.getPlugin().getNativePlugin();
        dependencyManager.injectDependencies(
                ProxyUtils.getRealClass(nativePlugin),
                nativePlugin
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

    private void invokeOnEnableCallbacks() {
        beanLifecycleInvoker.invokeOnEnable(dependencyManager.getBeanInstanceRegistry().asMapView());
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

    public void destroy(@NotNull Runnable onDisable, @NotNull Runnable shutdownHooks, @NotNull Runnable preDestroyProcessors) {
        currentPhase = ContextPhase.DESTROY;
        onDisable.run();
        shutdownHooks.run();
        currentPhase = ContextPhase.PRE_DESTROY_PROCESSORS;
        preDestroyProcessors.run();
        dependencyManager.clear();
        currentPhase = ContextPhase.CLEARED;
    }

    public BeanRegistrar getBeanRegistrar() {
        return beanRegistrar;
    }

    static @NotNull List<String> minimizePackageRoots(@NotNull Collection<String> packages) {
        Objects.requireNonNull(packages, "packages cannot be null");

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String pkg : packages) {
            if (pkg == null) {
                continue;
            }

            String trimmed = pkg.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }

        List<String> sortedBySpecificity = new ArrayList<>(normalized);
        sortedBySpecificity.sort(
                Comparator.comparingInt(ContextLifecycle::packageDepth)
                        .thenComparingInt(String::length)
                        .thenComparing(String::compareTo)
        );

        Set<String> roots = new LinkedHashSet<>();
        for (String candidate : sortedBySpecificity) {
            if (!isNestedUnderAny(candidate, roots)) {
                roots.add(candidate);
            }
        }

        List<String> orderedRoots = new ArrayList<>();
        for (String pkg : normalized) {
            if (roots.contains(pkg)) {
                orderedRoots.add(pkg);
            }
        }

        return orderedRoots;
    }

    private static boolean isNestedUnderAny(@NotNull String candidate, @NotNull Set<String> roots) {
        for (String root : roots) {
            if (candidate.equals(root) || candidate.startsWith(root + ".")) {
                return true;
            }
        }
        return false;
    }

    private static int packageDepth(@NotNull String packageName) {
        int depth = 1;
        for (int i = 0; i < packageName.length(); i++) {
            if (packageName.charAt(i) == '.') {
                depth++;
            }
        }
        return depth;
    }
}

